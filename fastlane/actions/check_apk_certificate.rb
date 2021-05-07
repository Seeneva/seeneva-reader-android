module Fastlane
  module Actions
    class CheckApkCertificateAction < Action
      def self.run(params)
        keystore_pem_cmd = "keytool -exportcert -keystore '#{params[:keystore]}' -alias '#{params[:alias]}' -storepass '#{params[:store_pass]}' -rfc".split

        keystore_pem = sh(keystore_pem_cmd, print_command: false, print_command_output: false).strip

        pem_regex =  /(-----BEGIN CERTIFICATE-----.*-----END CERTIFICATE-----)/m

        keystore_pem = (Regexp.last_match(0) if keystore_pem =~ pem_regex)

        UI.user_error!('No public certificate is available') if keystore_pem.nil? || keystore_pem.empty?

        UI.message("🔑 Required public certificate:\n#{keystore_pem}")

        params[:apk].each do |apk_path|
          apk_cert_cmd = "keytool -printcert -jarfile #{apk_path} -rfc".split

          apk_cert = sh(apk_cert_cmd, print_command_output: false).strip

          apk_cert = (Regexp.last_match(0) if apk_cert =~ pem_regex)

          UI.user_error!("No public certificate for apk #{apk_path}") if apk_cert.nil? || apk_cert.empty?

          UI.user_error!("Invalid certificate: #{apk_path}") if apk_cert != keystore_pem
        end

        UI.message('✨ All output APK has valid certificates')
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'Check APK public certificate'
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :keystore,
                                       env_name: 'SEENEVA_STORE_FILE',
                                       description: 'Path to a Keystore file',
                                       type: String,
                                       verify_block: proc do |value|
                                         UI.user_error!('No keystore file provided') unless value && !value.empty?
                                       end),
          FastlaneCore::ConfigItem.new(key: :alias,
                                       env_name: 'SEENEVA_KEY_ALIAS',
                                       description: 'Alias to use',
                                       type: String,
                                       sensitive: true,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('No alias was provided')
                                                       end
                                                     end),
          FastlaneCore::ConfigItem.new(key: :store_pass,
                                       env_name: 'SEENEVA_STORE_PASS',
                                       description: 'Keystore passwrord',
                                       type: String,
                                       sensitive: true,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('No keystore password was provided')
                                                       end
                                                     end),
          FastlaneCore::ConfigItem.new(key: :apk,
                                       env_name: 'FL_CHECK_CRT_APKS',
                                       description: 'Array of apk to check',
                                       type: Array,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('No apk was provided')
                                                       end
                                                     end)
        ]
      end

      def self.authors
        ['Seeneva']
      end

      def self.is_supported?(platform)
        platform == :android
      end
    end
  end
end

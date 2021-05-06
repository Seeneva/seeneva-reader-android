require 'base64'

module Fastlane
  module Actions
    class Base64DecodeToFileAction < Action
      def self.run(params)
        IO.write(params[:path], Base64.decode64(params[:data]))

        UI.message('Done!')
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'Decode base64 data to a file'
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :data,
                                       env_name: 'FL_BASE64_DECODE_TO_FILE_DATA',
                                       description: 'Base64 encoded data',
                                       type: String,
                                       sensitive: true,
                                       verify_block: proc do |value|
                                         UI.user_error!('Provide base64 data') unless value && !value.empty?
                                       end),
          FastlaneCore::ConfigItem.new(key: :path,
                                       env_name: 'FL_BASE64_DECODE_TO_FILE_PATH',
                                       description: 'Path to the decoded output file',
                                       type: String,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('Provide path to the decoded output file')
                                                       end
                                                     end)
        ]
      end

      def self.authors
        ['Seeneva']
      end

      def self.is_supported?(_platform)
        true
      end
    end
  end
end

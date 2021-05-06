require 'java-properties'

module Fastlane
  module Actions
    class PersistVersionAction < Action
      def self.run(params)
        name = params[:version_name]
        code = params[:version_code]

        properties = JavaProperties.load(params[:properties])

        properties[:'seeneva.versionName'] = name if name
        properties[:'seeneva.versionCode'] = code if code

        JavaProperties.write(properties, params[:properties])

        UI.message("Done! Name: #{name}, code: #{code}")
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'Persist app version'
      end

      def self.details
        'Persist app version to the Java properties file'
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :properties,
                                       env_name: 'FL_PERSIST_VERSION_PROPS',
                                       description: 'Path to properties file to use',
                                       type: String,
                                       default_value: './seeneva.properties'),
          FastlaneCore::ConfigItem.new(key: :version_name,
                                       env_names: %w[SEENEVA_VERSION_NAME FL_PERSIST_VERSION_NAME],
                                       description: 'App versionName to use',
                                       type: String,
                                       optional: true,
                                       verify_block: proc do |value|
                                         if value.empty?
                                           UI.user_error!("No version name for PersistVersionAction given, pass using `version_name: 'name'`")
                                         end
                                       end),
          FastlaneCore::ConfigItem.new(key: :version_code,
                                       env_names: %w[SEENEVA_VERSION_CODE FL_PERSIST_VERSION_CODE],
                                       description: 'App versionCode to use',
                                       type: Integer,
                                       optional: true,
                                       verify_block: proc do |value|
                                                       if value < 0
                                                         UI.user_error!('Version code should be a positive number')
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

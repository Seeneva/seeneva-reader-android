require 'octokit'

module Fastlane
  module Actions
    class ReleaseVersionCalcAction < Action
      # Tags should look like 'v0.1.0', 'v0.1.0-alpha', etc.
      TAG_REGEX = /^v(([0-9]+\.?){3}(-.+)?)$/

      def self.run(params)
        repo_filter = ->(r) { r.tag_name =~ TAG_REGEX && !r.draft }

        client = Octokit::Client.new(access_token: params[:token])
        client.auto_paginate = true

        releases = client.releases(params[:repo])

        version_tag = params[:tag]

        # If there is not tag was provided we will try to get it from last release
        if version_tag.nil? || version_tag.empty?
          # Take last release
          current_release = releases.find(&repo_filter)

          UI.user_error!("There is no proper GH release") if current_release.nil?

          version_tag = current_release.tag_name
        end

        version_name = (Regexp.last_match(1) if version_tag =~ TAG_REGEX)

        UI.user_error!("Can't get version name from tag #{version_tag}") if version_name.nil? || version_name.empty?

        # Count all releases and check that tag has a proper name
        # I've published app with versionCode == 2 to Google Console internal test by mistake :( And cannot detete it... That's why +2
        version_code = releases.select(&repo_filter).count + 2

        UI.message("Done! versionName: #{version_name}, versionCode: #{version_code}")

        puts version_name, version_code
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'Calculate app version name and code based on GitHub releases'
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :token,
                                       env_name: 'FL_RELEASE_VERSION_PAT',
                                       description: 'GitHub personal access token',
                                       type: String,
                                       sensitive: true,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('No GitHub PAT provided')
                                                       end
                                                     end),
          FastlaneCore::ConfigItem.new(key: :repo,
                                       env_name: 'GITHUB_REPOSITORY',
                                       description: 'GitHub repository',
                                       type: String,
                                       optional: true,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('Provide GitHub repository name e.g. Seeneva/seeneva-reader-android')
                                                       end
                                                     end),
          FastlaneCore::ConfigItem.new(key: :tag,
                                       env_name: 'FL_RELEASE_VERSION_TAG',
                                       description: 'Tag to use to calculate versionName. Should starts from "v" e.g v0.1.0, v0.1.0-alpha',
                                       type: String,
                                       optional: true,
                                       verify_block: proc do |value|
                                                       UI.user_error!('Invalid GitHub tag') if value !~ TAG_REGEX
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

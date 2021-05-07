require 'octokit'

module Fastlane
  module Actions
    class DeleteGhReleaseAction < Action
      def self.run(params)
        repo_name = params[:repo]
        tag = params[:tag]

        client = Octokit::Client.new(access_token: params[:token])
        release = client.release_for_tag(repo_name, tag)

        client.delete_release(release.url)
        client.delete_reference(repo_name, "tags/#{tag}")

        UI.message("Done! GitHub releaese #{release.id} and its tag was removed")
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'Delete GitHub release by tag name'
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :token,
                                       env_name: 'FL_DELETE_RELEASE_PAT',
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
                                       env_name: 'FL_DELETE_RELEASE_TAG',
                                       description: 'GitHub release tag',
                                       type: String,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('Provide GitHub release tag')
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

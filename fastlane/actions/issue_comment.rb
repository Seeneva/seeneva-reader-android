require 'octokit'

module Fastlane
  module Actions
    class IssueCommentAction < Action
      def self.run(params)
        client = Octokit::Client.new(access_token: params[:token])
        client.add_comment(params[:repo], params[:number], params[:msg])

        UI.message('Done!')
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'Send GitHub issue comment'
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :token,
                                       env_name: 'FL_ISSUE_COMMENT_PAT',
                                       description: 'GitHub personal access token',
                                       type: String,
                                       sensitive: true,
                                       verify_block: proc do |value|
                                         UI.user_error!('No GitHub PAT provided') unless value && !value.empty?
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
          FastlaneCore::ConfigItem.new(key: :number,
                                       env_name: 'FL_ISSUE_COMMENT_NUMBER',
                                       description: 'GitHub issue number',
                                       type: Integer,
                                       verify_block: proc do |value|
                                                       if value < 0
                                                         UI.user_error!('GitHub issue number should be a positive number')
                                                       end
                                                     end),
          FastlaneCore::ConfigItem.new(key: :msg,
                                       env_name: 'FL_ISSUE_COMMENT_MSG',
                                       description: 'GitHub comment body',
                                       type: String,
                                       verify_block: proc do |value|
                                                       unless value && !value.empty?
                                                         UI.user_error!('Provide GitHub issue comment body')
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

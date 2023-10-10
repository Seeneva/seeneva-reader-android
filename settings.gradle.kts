// It should be on the top of the file
// https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_management
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "Seeneva Reader"
rootProject.buildFileName = "build.gradle.kts"

include(":app", ":common", ":data", ":logic")


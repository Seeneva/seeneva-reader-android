// It should be on the top of the file
// https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_management
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

rootProject.buildFileName = "build.gradle.kts"

include(":app", ":common", ":data", ":logic")


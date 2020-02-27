import com.android.build.gradle.BaseExtension
import extension.Extension
import extension.RustBuildConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("android") version Version.KOTLIN apply false
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.0")
        //classpath(kotlin("gradle-plugin", Version.KOTLIN))
        //classpath("com.google.gms:google-services:4.2.0")
        //classpath("io.objectbox:objectbox-gradle-plugin:${Version.OBJECT_BOX}")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}

subprojects {
    apply {
        if (isAppLayer) {
            plugin("com.android.application")
        } else {
            plugin("com.android.library")
        }
        plugin("org.jetbrains.kotlin.android")
        plugin("org.jetbrains.kotlin.android.extensions")
    }

    configure<BaseExtension> {
        compileSdkVersion(29)

        defaultConfig {
            minSdkVersion(16)
            targetSdkVersion(29)

            versionCode = 1
            versionName = "1.0"

            multiDexEnabled = true
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        sourceSets.all {
            java.srcDir("src/$name/kotlin")
        }

        flavorDimensions(FlavorDimension.RUST_BUILD_TYPE)

        productFlavors {
            register("rustDebug") {
                require(this is ExtensionAware)

                setDimension(FlavorDimension.RUST_BUILD_TYPE)

                extra[Extension.RUST_BUILD_EXTENSION] = RustBuildConfig.Debug
            }

            register("rustRelease") {
                require(this is ExtensionAware)

                setDimension(FlavorDimension.RUST_BUILD_TYPE)

                extra[Extension.RUST_BUILD_EXTENSION] = RustBuildConfig.Release
            }
        }
    }

//    extensions.configure(AndroidExtensionsExtension::class) {
//        isExperimental = true
//    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    dependencies {
        "implementation"(kotlin("stdlib-jdk8", Version.KOTLIN))
        "implementation"(Deps.KOTLINX_COROUTINES_ANDROID)

        "implementation"(Deps.ANDROIDX_ANNOTATIONS)
        "implementation"(Deps.ANDROIDX_CORE_KTX)

        "implementation"(Deps.KOIN_ANDROID)
        "implementation"(Deps.KOIN_ANDROIDX_SCOPE)

        "implementation"(Deps.TINYLOG_API)
        "implementation"(Deps.TINYLOG_IMPL)

        "implementation"(Deps.THREETENABP)

        if (name != "common") {
            "implementation"(project(":common"))
        }

        "testImplementation"(Deps.KOTLINX_COROUTINES_TEST)
        "testImplementation"(kotlin("test-junit", Version.KOTLIN))
        "testImplementation"(Deps.ANDROIDX_TEST_JUNIT_KTX)

        "testImplementation"(Deps.MOCKK)
        "testImplementation"(Deps.KLUENT) {
            exclude("com.nhaarman.mockitokotlin2")
        }
        "testImplementation"(Deps.KOIN_TEST) {
            exclude("org.mockito")
        }

        //"androidTestImplementation"(Deps.MOCKK)
        "androidTestImplementation"(Deps.KLUENT) {
            exclude("com.nhaarman.mockitokotlin2")
        }
//        "androidTestImplementation"(Deps.KOIN_TEST) {
//            exclude("org.mockito")
//        }

        "androidTestImplementation"("androidx.test:runner:1.2.0")
        "androidTestImplementation"(Deps.ANDROIDX_TEST_JUNIT_KTX)
        "androidTestImplementation"("androidx.test.espresso:espresso-core:3.2.0")

        "androidTestImplementation"(Deps.KOTLINX_COROUTINES_TEST)
        "androidTestImplementation"(kotlin("test-junit", Version.KOTLIN))
        "androidTestImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.14.0")
    }
}

tasks.create("clean", Delete::class.java) {
    delete(rootProject.buildDir)
}

val Project.isAppLayer: Boolean
    get() = name == "app"

val Project.isDataLayer: Boolean
    get() = name == "data"

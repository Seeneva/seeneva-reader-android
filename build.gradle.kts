import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Version.KOTLIN
    kotlin("plugin.serialization") version Version.KOTLIN
    kotlin("android") version Version.KOTLIN apply false
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.2")
        //classpath(kotlin("gradle-plugin", Version.KOTLIN))

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
    }

    configure<BaseExtension> {
        //TODO update when fixed https://github.com/android/ndk/issues/1391 or build my own libz?
        ndkVersion = "21.3.6528147"
        compileSdkVersion(30)

        defaultConfig {
            minSdkVersion(16)
            targetSdkVersion(30)

            versionCode = 1
            versionName = "1.0"

            multiDexEnabled = true
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            isCoreLibraryDesugaringEnabled = true

            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        sourceSets.all {
            java.srcDir("src/$name/kotlin")
        }

        packagingOptions {
            pickFirst("META-INF/atomicfu.kotlin_module")
            pickFirst("META-INF/AL2.0")
            pickFirst("META-INF/LGPL2.1")
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
        // some Java 8 features
        // https://developer.android.com/studio/write/java8-support-table
        "coreLibraryDesugaring"(Deps.ANDROID_JAVA8_DESUGAR)

        implementation(Deps.KOTLINX_COROUTINES_ANDROID)

        implementation(Deps.ANDROIDX_ANNOTATIONS)
        implementation(Deps.ANDROIDX_CORE_KTX)

        //implementation(Deps.KOIN_ANDROID)
        implementation(Deps.KOIN_ANDROIDX_SCOPE) {
            exclude("androidx.viewpager")
        }

        implementation(Deps.TINYLOG_API)
        implementation(Deps.TINYLOG_IMPL)

        if (name != "common") {
            implementation(project(":common"))
        }

        testImplementation(Deps.KOTLINX_COROUTINES_TEST)
        testImplementation(Deps.KOTLINX_COROUTINES_CORE)
        testImplementation(kotlin("test-junit", Version.KOTLIN))
        testImplementation(Deps.ANDROIDX_TEST_JUNIT_KTX)

        testImplementation(Deps.MOCKK)
        testImplementation(Deps.KLUENT) {
            exclude("com.nhaarman.mockitokotlin2")
        }
        testImplementation(Deps.KOIN_TEST) {
            exclude("org.mockito")
        }

        //"androidTestImplementation"(Deps.MOCKK)
        "androidTestImplementation"(Deps.KLUENT) {
            exclude("com.nhaarman.mockitokotlin2")
        }

        "androidTestImplementation"("androidx.test:runner:1.3.0")
        "androidTestImplementation"(Deps.ANDROIDX_TEST_JUNIT_KTX)
        "androidTestImplementation"("androidx.test.espresso:espresso-core:3.3.0")

        "androidTestImplementation"(Deps.KOTLINX_COROUTINES_TEST)
        "androidTestImplementation"(kotlin("test-junit", Version.KOTLIN))
        "androidTestImplementation"(Deps.KOTLINX_SERIALIZATION_JSON)
    }
}

val Project.isAppLayer: Boolean
    get() = name == "app"

val Project.isDataLayer: Boolean
    get() = name == "data"

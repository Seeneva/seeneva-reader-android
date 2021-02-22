plugins {
    kotlin("kapt")
}

android {
    buildFeatures {
        viewBinding = true
    }

    splits {
        abi {
            isEnable = true

            reset()

            include(*Abi.values().map { it.abiName }.toTypedArray())

            isUniversalApk = true
        }
    }

    defaultConfig {
        applicationId = "com.almadevelop.comixreader"

        resConfigs("en", "ru")
        vectorDrawables.useSupportLibrary = true

        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

//    testOptions{
//        unitTests.setIncludeAndroidResources(true)
//    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":logic")) {
        exclude("androidx.viewpager")
    }

    implementation(Deps.ANDROIDX_APPCOMPAT) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.ANDROIDX_VIEW_PAGER) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.ANDROIDX_RECYCLER_VIEW)
    implementation(Deps.ANDROIDX_RECYCLER_VIEW_SELECTION)
    implementation(Deps.ANDROIDX_FRAGMENT_KTX) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.ANDROIDX_LIFECYCLE_SERVICE)
    implementation(Deps.ANDROIDX_LIFECYCLE_VIEWMODEL)
    implementation(Deps.ANDROIDX_LIFECYCLE_LIVEDATA)
    implementation(Deps.ANDROIDX_LIFECYCLE_JAVA8)
    implementation(Deps.ANDROIDX_PAGING_RUNTIME)
    implementation(Deps.ANDROIDX_CONSTRAINT_LAYOUT)
    implementation(Deps.ANDROIDX_WORK_RUNTIME)
    implementation(Deps.ANDROIDX_SWIPE_REFRESH_LAYOUT)

    implementation(Deps.MATERIAL) {
        exclude("androidx.viewpager")
    }

    implementation(Deps.KOIN_ANDROIDX_VIEWMODEL) {
        exclude("androidx.viewpager")
    }
    implementation(Deps.KOIN_ANDROIDX_WORKMANAGER)

    implementation(Deps.SCALE_IMAGE_VIEW)
}
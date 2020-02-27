plugins {
    kotlin("kapt")
    kotlin("android.extensions")
}

android {
    aaptOptions {
        noCompress("tflite")  // Your model'onPagesBatchPrepared file extension: "tflite", "lite", etc.
    }

    defaultConfig {
        applicationId = "com.almadevelop.comixreader"

        resConfigs("en", "ru")
        vectorDrawables.useSupportLibrary = true

//        externalNativeBuild {
//            cmake {
//                cppFlags "-std=c++14"
//            }
//        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

//    testOptions{
//        unitTests.setIncludeAndroidResources(true)
//    }

    packagingOptions {
        pickFirst("META-INF/atomicfu.kotlin_module")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":logic"))

    implementation(Deps.ANDROIDX_APPCOMPAT)
    implementation(Deps.ANDROIDX_RECYCLER_VIEW)
    implementation(Deps.ANDROIDX_RECYCLER_VIEW_SELECTION)
    implementation(Deps.ANDROIDX_FRAGMENT_KTX)
    implementation(Deps.ANDROIDX_LIFECYCLE_SERVICE)
    implementation(Deps.ANDROIDX_LIFECYCLE_VIEWMODEL)
    implementation(Deps.ANDROIDX_LIFECYCLE_LIVEDATA)
    implementation(Deps.ANDROIDX_LIFECYCLE_JAVA8)
    implementation(Deps.ANDROIDX_PAGING_RUNTIME)
    implementation(Deps.ANDROIDX_CONSTRAINT_LAYOUT)
    implementation(Deps.ANCROIDX_WORK_RUNTIME)

    implementation(Deps.MATERIAL)

    implementation(Deps.KOIN_ANDROIDX_VIEWMODEL)

    kapt(Deps.GLIDE_COMPILER)
}
//
//apply {
//    plugin("com.google.gms.google-services")
//}
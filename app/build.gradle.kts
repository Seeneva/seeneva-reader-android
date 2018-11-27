import com.android.build.gradle.api.SourceKind
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(28)

    aaptOptions {
        noCompress("tflite")  // Your model'onPagesBatchPrepared file extension: "tflite", "lite", etc.
    }


    defaultConfig {
        applicationId = "com.almadevelop.comixreader"
        minSdkVersion(16)
        targetSdkVersion(28)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(kotlin("stdlib-jdk7", KotlinCompilerVersion.VERSION))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.1.1")

    implementation("androidx.appcompat:appcompat:1.0.2")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-alpha2")
    implementation("androidx.core:core-ktx:1.0.1")

    implementation("com.google.firebase:firebase-ml-model-interpreter:16.2.4") {
        exclude("com.android.support")
    }

    implementation("com.github.bumptech.glide:glide:4.8.0")

    testImplementation("junit:junit:4.12")

    androidTestImplementation("androidx.test:runner:1.1.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.1")
}

apply {
    plugin("com.google.gms.google-services")
}

val archs = mapOf("x86" to "i686-linux-android")
val androidJniLibsPath = "${android.sourceSets.findByName("main")!!.jniLibs.srcDirs.first()}"
val rustPath = "$rootDir/comix_tensors"

val preBuildTask = tasks.findByName("preBuild")!!

archs.forEach { androidArch, rustArch ->
    val buildRustLibraryTask = tasks.create("buildRustLibrary_$rustArch", Exec::class.java){
        workingDir(rustPath)

        commandLine("cargo", "build", "--target=$rustArch")
    }

    val copyRustJniLibsTask: Task = tasks.create("copyRustJniLibs_$androidArch", Copy::class.java) {
        from("$rustPath/target/$rustArch/debug") {
            include("*.so")
        }
        into("$androidJniLibsPath/$androidArch")
    }

    preBuildTask.dependsOn(buildRustLibraryTask, copyRustJniLibsTask)
}
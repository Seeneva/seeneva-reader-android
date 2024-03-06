/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")

    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))

            implementation(project(":data"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.annotation)
            implementation(libs.androidx.core)
            api(libs.androidx.paging.common)
            api(libs.androidx.palette)

            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.androidx)

            implementation(libs.coil)

            implementation(libs.rTree)

            implementation(libs.tinylog.api)
            implementation(libs.tinylog.impl)
        }

        named("androidUnitTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)

                implementation(libs.mockk)
            }
        }

        named("androidInstrumentedTest") {
            dependencies {
                implementation(libs.android.test.runner)
                implementation(libs.android.test.junit)

                implementation(libs.kotlin.test.junit)
                implementation(libs.kotlinx.coroutines.test)

                implementation(libs.koin.test)

                implementation(libs.kluent.android.get().toString()) {
                    exclude("com.nhaarman.mockitokotlin2")
                }
            }
        }
    }
}

android {
    defaultConfig {
        namespace = "app.seeneva.reader.logic"

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.targetSdk = libs.versions.androidTargetSdk.get().toInt()

//    aaptOptions{
//        //Add the following lines to this code block to prevent Android from compressing TensorFlow Lite model files
//        noCompress("tflite")
//    }
}

dependencies {
    // some Java 8 features
    // https://developer.android.com/studio/write/java8-support-table
    "coreLibraryDesugaring"(libs.google.android.java.desugar)
}


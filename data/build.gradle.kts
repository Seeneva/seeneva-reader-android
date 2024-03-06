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

    alias(libs.plugins.ksp)
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
        }

        androidMain.dependencies {
            implementation(libs.androidx.annotation)
            implementation(libs.androidx.core)
            implementation(libs.androidx.room)

            implementation(libs.koin.androidx)

            implementation(libs.tinylog.api)
            implementation(libs.tinylog.impl)
        }

        // val androidInstrumentedTest by getting
        named("androidInstrumentedTest") {
            dependencies {
                implementation(libs.android.test.runner)
                implementation(libs.android.test.junit)

                implementation(libs.kotlin.test.junit)

                implementation(libs.faker)
                implementation(libs.kluent.android.get().toString()) {
                    exclude("com.nhaarman.mockitokotlin2")
                }
            }
        }
    }
}

android {
    //https://developer.android.com/studio/projects/gradle-external-native-builds
    externalNativeBuild {
        cmake {
            path = rootDir.resolve("native/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    defaultConfig {
        namespace = "app.seeneva.reader.data"

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "${projectDir.resolve("schemas")}")
            arg("room.incremental", true.toString())
            arg("room.expandProjection", true.toString())
        }

        ndk {
            abiFilters += Abi.values().map { it.abiName }
        }

        externalNativeBuild {
            cmake {
                targets("cargo-build")
            }
        }
    }

    flavorDimensions += RustBuildTypeFlavor.NAME

    productFlavors {
        register(RustBuildTypeFlavor.RUST_DEBUG) {
            dimension = RustBuildTypeFlavor.NAME
        }

        register(RustBuildTypeFlavor.RUST_RELEASE) {
            dimension = RustBuildTypeFlavor.NAME

            externalNativeBuild {
                cmake {
                    arguments("-DRUST_RELEASE=ON")
                }
            }
        }
    }

    buildTypes {
        named("release") {
            externalNativeBuild {
                cmake {
                    arguments("-DRUST_DEBUG_LOGS=OFF")

                    if (hasProperty(extension.PROP_NO_DEB_SYMBOLS)) {
                        // do not generate native debug symbols
                        arguments("-DLIB_DEB_SYMBOLS=OFF")
                    }
                }
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.targetSdk = libs.versions.androidTargetSdk.get().toInt()
}

dependencies {
    "kspCommonMainMetadata"(libs.androidx.room.compiler)
    "kspAndroid"(libs.androidx.room.compiler)
    "kspAndroidTest"(libs.androidx.room.compiler)

    // some Java 8 features
    // https://developer.android.com/studio/write/java8-support-table
    "coreLibraryDesugaring"(libs.google.android.java.desugar)
}
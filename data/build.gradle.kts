/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask

plugins {
    kotlin("kapt")
}

android {
    //https://developer.android.com/studio/projects/gradle-external-native-builds
    externalNativeBuild {
        cmake {
            path = rootDir.resolve("native/CMakeLists.txt")
        }
    }

    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments(
                    mapOf(
                        "room.schemaLocation" to "${projectDir.resolve("schemas")}",
                        "room.incremental" to "true",
                        "room.expandProjection" to "true"
                    )
                )
            }
        }

        ndk {
            abiFilters += Abi.values().map { it.abiName }
        }
    }

    flavorDimensions(RustBuildTypeFlavor.NAME)

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

    libraryVariants.configureEach { setCustomCmakeTask(this) }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(Deps.ANDROIDX_ROOM_KTX)

    kapt(Deps.ANDROIDX_ROOM_COMPILER)
}

fun setCustomCmakeTask(variant: BaseVariant) {
    variant.externalNativeBuildProviders
        .single()
        .invoke {
            doLast {
                // generateJsonModel* task running before externalNativeBuild* task
                val generator =
                    tasks.getByName<ExternalNativeBuildJsonTask>("generateJsonModel${variant.name.capitalize()}")
                        .externalNativeJsonGenerator
                        .get()

                //AndroidBuildGradleJsons.getNativeBuildMiniConfigs(generator.nativeBuildConfigurationsJsons, generator.stats)

                //start Cargo build script for each ABI
                generator.abis.forEach {
                    exec { commandLine(it.cmake!!.cmakeArtifactsBaseFolder.resolve("cargo_build.sh")) }.assertNormalExitValue()
                }
            }
        }
}
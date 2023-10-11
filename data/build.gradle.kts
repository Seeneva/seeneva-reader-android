/*
 *  This file is part of Seeneva Android Reader
 *  Copyright (C) 2021-2023 Sergei Solodovnikov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id(Plugin.KSP)
}

android {
    //https://developer.android.com/studio/projects/gradle-external-native-builds
    externalNativeBuild {
        cmake {
            path = rootDir.resolve("native/CMakeLists.txt")
            version = "3.10.2"
        }
    }

    defaultConfig {
        namespace = "app.seeneva.reader.data"

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
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(Deps.ANDROIDX_ROOM_KTX)

    ksp(Deps.ANDROIDX_ROOM_COMPILER)
}
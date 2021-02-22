import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask

plugins {
    kotlin("kapt")
}

android {
    externalNativeBuild {
        cmake {
            path = rootDir.resolve("comix_tensors/CMakeLists.txt")
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
        getByName("release") {
            externalNativeBuild {
                cmake {
                    arguments("-DRUST_DEBUG_LOGS=OFF")
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
                // generateJsonModel* task running before externalNativeBuild* tash
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
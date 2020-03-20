import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.gradle.osdetector.OsDetector
import extension.Extension
import extension.RustBuildConfig

plugins {
    kotlin("kapt")
    id("com.google.osdetector")
}

android {
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments = mapOf(
                    "room.schemaLocation" to "${projectDir.resolve("schemas")}",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
    }

    libraryVariants.configureEach {
        setRustTask(this)
        //asanTask(this)
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(Deps.ANDROIDX_ROOM_KTX)

    kapt(Deps.ANDROIDX_ROOM_COMPILER)
}

private val ndkToolchainsPath by lazy {
    val ndkHostTag = ndkHostTag(project.extensions.getByType(OsDetector::class))

    android.ndkDirectory.toPath().resolve("toolchains/llvm/prebuilt/$ndkHostTag")
}

private val jniLibsPath = { name: String ->
    android.sourceSets
        .getByName(name)
        .jniLibs
        .srcDirs
        .first()
        .toPath()
}

fun setRustTask(variant: LibraryVariant) {
    val minSdkVersion = variant.mergedFlavor.minSdkVersion!!.apiLevel

    val toolchainsBinPath = ndkToolchainsPath.resolve("bin")

    val rustPath = rootDir.toPath().resolve("comix_tensors")

    val rustBuildFlavor =
        variant.productFlavors.first { it.dimension == FlavorDimension.RUST_BUILD_TYPE }

    val androidJniLibsPath = jniLibsPath(rustBuildFlavor.name)

    for (abi in Abi.values()) {
        val taskName = "build${rustBuildFlavor.name.capitalize()}Library_${abi.abiName}"

        if (kotlin.runCatching { tasks.named(taskName) }
                .map { true }
                .recoverCatching {
                    when (it) {
                        is UnknownTaskException -> false
                        else -> throw it
                    }
                }
                .getOrThrow()) {
            continue
        }

        val buildRustLibraryTask = tasks.register(taskName) {
            group = TaskGroup.RUST
            description =
                "Build ${abi.rustTriple} shared library from Rust source and copy it to the Android jniLibs folder"

            doLast {
                val rustBuildConfig = rustBuildFlavor.let {
                    (android.productFlavors.findByName(it.name) as ExtensionAware).extra.get(
                        Extension.RUST_BUILD_EXTENSION
                    ) as RustBuildConfig
                }

                //execute Rust Cargo build
                val rustBuildResult = exec {
                    workingDir(rustPath.toString())

                    environment(
                        //setup cargo. It can be overrided via .cargo/config TOML file
                        //https://doc.rust-lang.org/cargo/reference/config.html
                        *abi.rustTriple.replace('-', '_').toUpperCase().let {
                            val cargoTriple = abi.rustTriple.replace('-', '_').toUpperCase()

                            arrayOf(
                                "CARGO_TARGET_${cargoTriple}_LINKER" to
                                        "$toolchainsBinPath/${abi.cxxLinker(minSdkVersion)}",
                                "CARGO_TARGET_${cargoTriple}_AR" to
                                        "$toolchainsBinPath/${abi.ar}"
                            )
                        },
                        //set libc linker and ar
                        //You can alse set specific flags by CXX_${abi.rustTriple} (CXX_i686-linux-android)
                        //https://github.com/alexcrichton/cc-rs#external-configuration-via-environment-variables
                        "CC" to "$toolchainsBinPath/${abi.cLinker(minSdkVersion)}",
                        "CXX" to "$toolchainsBinPath/${abi.cxxLinker(minSdkVersion)}",
                        "AR" to "$toolchainsBinPath/${abi.ar}",
                        "CXXFLAGS" to abi.compileFlags(minSdkVersion),
                        "CFLAGS" to abi.compileFlags(minSdkVersion)
                    )

                    commandLine(rustBuildConfig.cmdArgs(abi))
                }

                rustBuildResult.assertNormalExitValue()

                copy {
                    from(
                        rustPath.resolve("target/${abi.rustTriple}/${rustBuildConfig.name.toLowerCase()}")
                            .toString()
                    ) {
                        include("*.so")
                    }

                    into(androidJniLibsPath.resolve(abi.abiName).toString())
                }
            }
        }

        variant.preBuildProvider.dependsOn(buildRustLibraryTask)
    }
}

//fun asanTask(variant: LibraryVariant) {
//    if (!variant.buildType.isDebuggable) {
//        return
//    }
//
//    val asanNdkLibPath = Files.newDirectoryStream(ndkToolchainsPath.resolve("lib64/clang"))
//        .use { it.first() }
//        .resolve("lib/linux")
//
//    val wrapShNdkPath = android.ndkDirectory.toPath().resolve("wrap.sh")
//
//    val debugJniLibsPath = jniLibsPath(variant.buildType.name)
//    val resourcesLibPath = android.sourceSets
//        .findByName(variant.buildType.name)!!
//        .resources
//        .srcDirs
//        .first()
//        .toPath()
//        .resolve("lib")
//
//    variant.preBuildProvider.configure {
//        doLast {
//            for (abi in Abi.values()) {
//                //copy ASan
//                //https://android.googlesource.com/platform/ndk/+/master/docs/BuildSystemMaintainers.md#sanitizers
//                copy {
//                    from(asanNdkLibPath.toString()) {
//                        include("libclang_rt.asan-${abi.arch}-android.so")
//                    }
//
//                    into(debugJniLibsPath.resolve(abi.abiName).toString())
//                }
//
//                copy {
//                    from(wrapShNdkPath.toString()) {
//                        include("asan.${abi.abiName}.sh")
//                        rename { "wrap.sh" }
//                    }
//
//                    into(resourcesLibPath.resolve(abi.abiName).toString())
//                }
//            }
//        }
//    }
//}
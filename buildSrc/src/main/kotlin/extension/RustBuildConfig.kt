package extension

import Abi

enum class RustBuildConfig(private val cmdArgs: String? = null) {
    Debug, Release("--release");

    fun cmdArgs(abi: Abi) =
        arrayListOf("cargo", "build", "--target=${abi.rustTriple}").also {
            if (!cmdArgs.isNullOrBlank()) {
                it += cmdArgs
            }
        }
}
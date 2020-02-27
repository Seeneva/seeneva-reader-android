import com.google.gradle.osdetector.OsDetector
import org.gradle.internal.os.OperatingSystem

@Suppress("EnumEntryName")
enum class Abi(val abiName: String, val arch: String, val triple: String, val bit64: Boolean = false) {
    x86("x86", "i686", "i686-linux-android"),
    armeabi_v7a("armeabi-v7a", "arm", "armv7a-linux-androideabi"),
    x86_64("x86_64", "x86_64", "x86_64-linux-android", true),
    arm64_v8a("arm64-v8a", "aarch64", "aarch64-linux-android", true);

    /**
     * Name of the archiver bin file
     */
    val ar: String
        get() = if (this == armeabi_v7a) {
            "${ARM_BIN_PREFIX}ar"
        } else {
            "$triple-ar"
        }

    /**
     * Triple name of the ABI
     */
    val rustTriple: String
        get() = if (this == armeabi_v7a) {
            RUST_ARM_TRIPLE
        } else {
            triple
        }

    /**
     * @param minVersion min SDK version of the app
     * @return name of the C linker
     */
    fun cLinker(minVersion: Int) = "$triple${linkerVersion(minVersion)}-clang"

    /**
     * @param minVersion min SDK version of the app
     * @return name of the CPP linker
     */
    fun cxxLinker(minVersion: Int) = "$triple${linkerVersion(minVersion)}-clang++"

    /**
     * @param minVersion min SDK version of the app
     * @return C/CPP compile compileFlags
     */
    fun compileFlags(minVersion: Int) = "$DEFINE_ANDROID_API=${linkerVersion(minVersion)}"

    /**
     * Officially Android 64 available only from SDK 21.
     * Compare provided min SDK version and return correct version
     * @param buildMinVersion min SDK version of the app
     * @return correct min sdk version
     */
    private fun linkerVersion(buildMinVersion: Int): Int {
        require(buildMinVersion >= 0) { "Version Cannot be negative" }

        return if (!bit64) {
            buildMinVersion
        } else {
            buildMinVersion.coerceAtLeast(MIN_VERSION_64)
        }
    }

    private companion object {
        private const val MIN_VERSION_64 = 21

        private const val RUST_ARM_TRIPLE = "armv7-linux-androideabi"

        private const val ARM_BIN_PREFIX = "arm-linux-androideabi-"

        private const val DEFINE_ANDROID_API = "-D__ANDROID_API__"
    }
}

/**
 * Return current machine host tag. Throw [IllegalArgumentException] if OS does not supported
 *
 * @see <a href="https://developer.android.com/ndk/guides/other_build_systems#overview">NDK host tag</a>
 */
fun ndkHostTag(osDetector: OsDetector): String {
    val os = OperatingSystem.current()

    return when {
        os.isLinux -> "linux-x86_64"
        os.isMacOsX -> "darwin-x86_64"
        os.isWindows -> {
            if (osDetector.arch.contains("64")) {
                "windows-x86_64"
            } else {
                "windows"
            }
        }
        else -> throw IllegalStateException("Unsupported OS $os")
    }
}
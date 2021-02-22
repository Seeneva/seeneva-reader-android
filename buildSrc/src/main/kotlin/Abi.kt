/**
 * Supported Application Binary Interface (ABI)
 * @param abiName name of the ABI
 */
enum class Abi(val abiName: String) {
    X86("x86"),
    ARMEABI_V7A("armeabi-v7a"),
    X86_64("x86_64"),
    ARM64_V8A("arm64-v8a");
}
use cc;
use std::fs::read_dir;
use std::env;

fn main() {
    let lzma_path = env::current_dir().unwrap().join("vendor").join("lzma");

    let files = [
        "7zFile.c",
        "7zStream.c",
        "7zCrc.c",
        "CpuArch.c",
        "7zCrcOpt.c",
        "7zArcIn.c",
        "7zAlloc.c",
        "7zBuf.c",
        "7zDec.c",
        "Lzma2Dec.c",
        "LzmaDec.c",
        "Bcj2.c",
        "Delta.c",
        "Bra.c",
        "Bra86.c",
        "BraIA64.c",
    ]
    .into_iter()
    .map(|c_name| lzma_path.join(c_name));

    cc::Build::new().files(files).compile("liblzma.a");
}

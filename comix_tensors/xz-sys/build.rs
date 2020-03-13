use autotools;

fn main() {
    println!("cargo:rerun-if-env-changed=CC");
    println!("cargo:rerun-if-env-changed=AR");
    println!("cargo:rerun-if-env-changed=CFLAGS");

    let dst = autotools::Config::new("vendor/xz")
        .reconf("--install")
        .disable_shared()
        .enable("werror", None)
        .disable("rpath", None)
        .disable("xz", None)
        .disable("xzdec", None)
        .disable("lzmadec", None)
        .disable("lzmainfo", None)
        .disable("scripts", None)
        .disable("doc", None)
        .build();

    //https://doc.rust-lang.org/cargo/reference/build-scripts.html#outputs-of-the-build-script
    //lzma headers
    println!("cargo:include={}", dst.join("include").display());
    println!(
        "cargo:rustc-link-search=native={}",
        dst.join("lib").display()
    );
}

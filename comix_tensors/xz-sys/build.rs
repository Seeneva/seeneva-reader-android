use std::env;

use autotools;
use build_deps;
use pkg_config;

fn main() {
    let links_lib_name = env::var("CARGO_MANIFEST_LINKS").unwrap();

    if let Ok(_) = pkg_config::Config::new()
        .atleast_version(&env::var("CARGO_PKG_VERSION").unwrap())
        .probe(&format!("lib{}", &links_lib_name))
    {
        println!("Target already has LZMA library");
    } else {
        println!("Build LZMA library");

        let build_path = autotools::Config::new("vendor/xz")
            .reconf("-if")
            //from autotools 0.2.2 it is required to set 'host' to cross compile properly
            .config_option("host", Some(env::var("HOST").unwrap().as_str()))
            .disable_shared()
            .enable_static()
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
        //LZMA headers

        //this needed to provide DEP_LZMA_PREFIX env variable to libarchive-sys
        //read more about DEP_{LINK_NAME}_{KEY} https://doc.rust-lang.org/cargo/reference/build-scripts.html#-sys-packages
        //https://rurust.github.io/cargo-docs-ru/build-script.html
        //println!("cargo:prefix={}", build_path.join("include").display());

        // !! Every 'links' library set DEP_${LINK}_ROOT env variable !!

        println!(
            "cargo:rustc-link-search=native={}",
            build_path.join("lib").display()
        );

        build_deps::rerun_if_changed_paths("vendor/xz/src/**/*.c")
            .and(build_deps::rerun_if_changed_paths("vendor/xz/src/**/*.h"))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/xz/src/**/*.h.in",
            ))
            .and(build_deps::rerun_if_changed_paths("vendor/xz/**/*.ac"))
            .and(build_deps::rerun_if_changed_paths("vendor/xz/**/*.am"))
            .and(build_deps::rerun_if_changed_paths("vendor/xz/**/*.cmake"))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/xz/**/CMakeLists.txt",
            ))
            .expect("Can't set return_if_changed globs");

        //https://doc.rust-lang.org/cargo/reference/build-scripts.html#cargorerun-if-env-changedname
        println!("cargo:rerun-if-env-changed=CC");
        println!("cargo:rerun-if-env-changed=CFLAGS");
    }

    println!("cargo:rustc-link-lib={}", &links_lib_name);
}

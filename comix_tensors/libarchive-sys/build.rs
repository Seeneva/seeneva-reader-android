use std::env;
use std::path::PathBuf;

use bindgen;
use build_deps;
use cmake;
use pkg_config;

use build_helper::*;

fn main() {
    let links_lib_name = env::var("CARGO_MANIFEST_LINKS").unwrap();
    let target = env::var("CARGO_CFG_TARGET_OS").unwrap();

    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    let archive_path = env::current_dir().unwrap().join("vendor/libarchive");

    // Additional headers for different platforms
    let archive_headers = {
        let mut archive_headers = vec![];

        if target == ANDROID {
            archive_headers.push(archive_path.join("contrib/android/include/"));
            archive_headers.push(archive_path.join("contrib/android/config/"));
        }

        archive_headers
    };

    let opt_build_path = if let Ok(_) = pkg_config::Config::new()
        .atleast_version(&env::var("CARGO_PKG_VERSION").unwrap())
        .probe(&format!("lib{}", &links_lib_name))
    {
        println!("Target already has archive library");

        println!("cargo:rustc-link-lib={}", &links_lib_name);

        None
    } else {
        println!("Build archive library");

        let archive_path = env::current_dir().unwrap().join("vendor/libarchive");

        let build_path = {
            let mut achive_cmake = cmake::Config::new(archive_path.as_path());

            achive_cmake
                .define("ENABLE_MBEDTLS", "OFF")
                .define("ENABLE_NETTLE", "OFF")
                .define("ENABLE_OPENSSL", "OFF")
                .define("ENABLE_LIBB2", "OFF")
                .define("ENABLE_LZ4", "OFF")
                .define("ENABLE_LZO", "OFF")
                .define("ENABLE_LZMA", "ON")
                .define("ENABLE_ZSTD", "OFF")
                .define("ENABLE_ZLIB", "ON")
                .define("ENABLE_BZip2", "OFF")
                .define("ENABLE_LIBXML2", "OFF")
                .define("ENABLE_EXPAT", "OFF")
                .define("ENABLE_PCREPOSIX", "OFF")
                .define("ENABLE_LibGCC", "OFF")
                .define("ENABLE_CNG", "OFF")
                .define("ENABLE_TAR", "OFF")
                .define("ENABLE_TAR_SHARED", "OFF")
                .define("ENABLE_CAT", "OFF")
                .define("ENABLE_CAT_SHARED", "OFF")
                .define("ENABLE_CPIO", "OFF")
                .define("ENABLE_CPIO_SHARED", "OFF")
                .define("ENABLE_XATTR", "OFF")
                .define("ENABLE_ACL", "OFF")
                .define("ENABLE_ICONV", "OFF")
                .define("ENABLE_TEST", "OFF")
                .define("ENABLE_COVERAGE", "OFF")
                .custom_target_define();

            // Add paths to the lzma library if we build it locally
            if let Ok(lzma_root) = env::var("DEP_LZMA_ROOT") {
                //So...lzma library doesn't provide cmake files to set as dependency
                //It is a workaround

                let lzma_root = PathBuf::from(lzma_root);

                achive_cmake
                    .define("LIBLZMA_LIBRARY", lzma_root.join("lib/liblzma.a"))
                    .define("LIBLZMA_INCLUDE_DIR", lzma_root.join("include"));
            }

            // Apply additional headers
            for header_dir in &archive_headers {
                achive_cmake.cflag(format!("-I{}", header_dir.display()));
            }

            achive_cmake.build()
        };

        println!(
            "cargo:rustc-link-search=native={}",
            build_path.join("lib").display()
        );

        //libarchive builds shared and static libraries together. We need only static
        println!("cargo:rustc-link-lib=static={}", &links_lib_name);

        build_deps::rerun_if_changed_paths("vendor/libarchive/libarchive/*.c")
            .and(build_deps::rerun_if_changed_paths(
                "vendor/libarchive/libarchive/*.h",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/libarchive/libarchive/*.h.in",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/libarchive/**/*.ac",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/libarchive/**/*.am",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/libarchive/**/*.cmake",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/libarchive/**/CMakeLists.txt",
            ))
            .expect("Can't set return_if_changed globs");

        println!("cargo:rerun-if-env-changed=CC");
        println!("cargo:rerun-if-env-changed=CFLAGS");

        Some(build_path)
    };

    // for zip
    println!("cargo:rustc-link-lib=z");
    // for 7z archives
    println!("cargo:rustc-link-lib=lzma");

    println!("cargo:rerun-if-changed=wrapper.h");

    // Generate C bindings
    let mut bindgen_builder = bindgen::builder()
        .whitelist_recursively(false)
        .whitelist_function(r"^archive_.+")
        .whitelist_type(r"^archive[_\w]*")
        .whitelist_var(r"^ARCHIVE_\w+")
        .whitelist_var(r"^AE[_\w]+")
        .whitelist_type(r"^la_.+")
        .blacklist_item(r"^ARCHIVE_VERSION_\w+")
        // use enum in the wrapper.h
        .blacklist_item("ARCHIVE_EOF")
        .blacklist_item("ARCHIVE_OK")
        .blacklist_item("ARCHIVE_RETRY")
        .blacklist_item("ARCHIVE_WARN")
        .blacklist_item("ARCHIVE_FAILED")
        .blacklist_item("ARCHIVE_FATAL")
        .header("wrapper.h")
        .rustfmt_bindings(true);

    if let Some(archive_path) = opt_build_path.as_ref() {
        bindgen_builder =
            bindgen_builder.clang_arg(format!("-I{}", archive_path.join("include").display()));
    }

    // Apply additional headers
    for header_dir in &archive_headers {
        bindgen_builder = bindgen_builder.clang_arg(format!("-I{}", header_dir.display()));
    }

    bindgen_builder
        .generate()
        .expect("Can't generate Archive bindings")
        .write_to_file(out_dir.join("bindings.rs"))
        .expect("Can't write Archive bindings");
}

use std::env;
use std::ffi::OsString;
use std::path::PathBuf;

use autotools;
use bindgen;
use build_deps;
use cc;
use pkg_config;

use build_helper::*;

fn main() {
    let links_lib_name = env::var("CARGO_MANIFEST_LINKS").unwrap();
    let target = env::var("CARGO_CFG_TARGET_OS").unwrap();

    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    //check is leptonica already presented
    let opt_build_path = if let Ok(library) = pkg_config::Config::new()
        .atleast_version(&env::var("CARGO_PKG_VERSION").unwrap())
        .probe(&format!("lib{}", &links_lib_name))
    {
        println!("Target already has Leptonica library");

        //set rerun for regenerate bindgen
        for include_dir in library.include_paths.as_slice() {
            build_deps::rerun_if_changed_paths(&format!("{}/**", include_dir.display()))
                .expect("Can't set return_if_changed globs");
        }

        None
    } else {
        println!("Build Tesseract library");

        // You can use CMake, but it has issue with DISABLED_LEGACY_ENGINE option on 4.1.1
        // https://github.com/tesseract-ocr/tesseract/issues/3006
        // This issue was fixed on master branch

        let mut build_config = autotools::Config::new("vendor/tesseract");

        build_config
            .reconf("-if")
            //from autotools 0.2.2 it is required to set 'host' to cross compile properly
            .config_option("host", Some(env::var("TARGET").unwrap().as_str()))
            .disable("graphics", None)
            .disable("legacy", None)
            .disable("openmp", None)
            .disable("tessdata-prefix", None)
            .disable_shared()
            .enable_static();

        if target == ANDROID {
            // We should remove pkgConfig's lib dir path to prevent usage of HOST libraries

            let lib_dirs = env::var("DEP_LEPT_ROOT")
                .ok()
                .map(|lept_root| PathBuf::from(lept_root).join("lib/pkgconfig"))
                .into_iter()
                .fold(OsString::new(), |mut s, path| {
                    if !s.is_empty() {
                        s.push(":");
                    }

                    s.push(path);

                    s
                });

            build_config.env("PKG_CONFIG_LIBDIR", lib_dirs);
        }

        let build_path = build_config.build();

        println!(
            "cargo:rustc-link-search=native={}",
            build_path.join("lib").display()
        );

        if target == ANDROID {
            println!("cargo:rustc-link-lib=c++abi");
        }

        if let Some(cpp_stdlib) = get_cpp_link_stdlib(&target) {
            println!("cargo:rustc-link-lib={}", cpp_stdlib);
        }

        build_deps::rerun_if_changed_paths("vendor/tesseract/src/**/*.cpp")
            .and(build_deps::rerun_if_changed_paths(
                "vendor/tesseract/src/**/*.h",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/tesseract/src/**/*.h.in",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/tesseract/**/*.ac",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/tesseract/**/*.am",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/tesseract/**/*.cmake",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/tesseract/**/CMakeLists.txt",
            ))
            .expect("Can't set return_if_changed globs");

        println!("cargo:rerun-if-env-changed=CC");
        println!("cargo:rerun-if-env-changed=CXX");
        println!("cargo:rerun-if-env-changed=CFLAGS");
        println!("cargo:rerun-if-env-changed=CXXFLAGS");

        Some(build_path)
    };

    println!("cargo:rustc-link-lib={}", &links_lib_name);

    let opt_tesseract_include = opt_build_path
        .as_ref()
        .map(|build_path| build_path.join("include"));

    //compile extra tesseract C Api needed for the project
    {
        let mut extra_cc = cc::Build::new();

        extra_cc
            .cpp(true)
            .file("extra/extraapi.cpp")
            .out_dir(out_dir.join("extraapi"));

        if let Some(tesseract_include) = opt_tesseract_include.as_ref() {
            extra_cc.include(tesseract_include);
        }

        extra_cc.compile("libtesseractextra.a");

        build_deps::rerun_if_changed_paths("extra/*.cpp")
            .and(build_deps::rerun_if_changed_paths("extra/*.h"))
            .expect("Can't set return_if_changed globs");
    }

    println!("cargo:rerun-if-changed=wrapper.h");

    let mut bindgen_builder = bindgen::builder()
        .whitelist_recursively(false)
        .whitelist_function("^Tess.*")
        .whitelist_type("^Tess.*")
        .whitelist_type("ETEXT_DESC")
        //leptonica types. Do not generate structs for them
        .blacklist_type("Pix")
        .blacklist_type("Pixa")
        .blacklist_type("Boxa")
        .header("wrapper.h")
        .rustfmt_bindings(true);

    if let Some(tesseract_include) = opt_tesseract_include.as_ref() {
        bindgen_builder = bindgen_builder.clang_arg(format!("-I{}", tesseract_include.display()));
    }

    bindgen_builder
        .generate()
        .expect("Can't generate Tesseract bindings")
        .write_to_file(out_dir.join("bindings.rs"))
        .expect("Can't write Tesseract bindings");
}

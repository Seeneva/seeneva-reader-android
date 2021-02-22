use std::env;
use std::path::PathBuf;

use autotools;
use bindgen;
use build_deps;
use cc;
use pkg_config;

fn main() {
    let links_lib_name = env::var("CARGO_MANIFEST_LINKS").unwrap();

    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    //check is leptonica already present
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
        println!("Build Leptonica library");

        // build using autotools
        // ignore any image libs, we do not need them for now
        let build_path = autotools::Config::new("vendor/leptonica")
            .reconf("-if")
            //from autotools 0.2.2 it is required to set 'host' to cross compile properly
            .config_option("host", Some(env::var("HOST").unwrap().as_str()))
            .without("zlib", None)
            .without("libpng", None)
            .without("jpeg", None)
            .without("giflib", None)
            .without("libtiff", None)
            .without("libwebp", None)
            .without("libwebpmux", None)
            .without("libopenjpeg", None)
            .disable("programs", None)
            .build();

        println!(
            "cargo:rustc-link-search=native={}",
            build_path.join("lib").display()
        );

        //allow use headers using DEP_LEPT_PREFIX env var
        println!("cargo:prefix={}", build_path.display());

        build_deps::rerun_if_changed_paths("vendor/leptonica/src/*.c")
            .and(build_deps::rerun_if_changed_paths(
                "vendor/leptonica/src/*.h",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/leptonica/src/*.h.in",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/leptonica/**/*.ac",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/leptonica/**/*.am",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/leptonica/**/*.cmake",
            ))
            .and(build_deps::rerun_if_changed_paths(
                "vendor/leptonica/**/CMakeLists.txt",
            ))
            .expect("Can't set return_if_changed globs");

        println!("cargo:rerun-if-env-changed=CC");
        println!("cargo:rerun-if-env-changed=CFLAGS");

        Some(build_path)
    };

    println!("cargo:rustc-link-lib={}", &links_lib_name);

    println!("cargo:rerun-if-changed=wrapper.h");

    let opt_leptonica_include = opt_build_path
        .as_ref()
        .map(|build_path| build_path.join("include"));

    {
        println!("Compile extra API");

        let mut extra_cc = cc::Build::new();

        extra_cc
            .file("extra/extraapi.c")
            .out_dir(out_dir.join("extraapi"));

        if let Some(include_path) = opt_leptonica_include.as_ref() {
            extra_cc.include(include_path);
        }

        extra_cc.compile("libleptextra.a");

        build_deps::rerun_if_changed_paths("extra/*.c")
            .and(build_deps::rerun_if_changed_paths("extra/*.h"))
            .expect("Can't set return_if_changed globs");
    }

    println!("Generate Leptonica bindings");

    let mut bindgen_builder = bindgen::builder()
        .whitelist_recursively(false)
        .whitelist_type(r"(?i)^\w*pix\w*$")
        .whitelist_type(r"(?i)^boxa*$")
        .whitelist_type(r"(?i)^numa*$")
        .whitelist_type(r"(?i)^sela?$")
        .whitelist_type(r"(?i)^ptaa?$")
        .whitelist_type(r"(?i)^sarray")
        .whitelist_type(r"(?i)^ccborda?$")
        .whitelist_type(r"(?i)^l_\w*$")
        .whitelist_type(r"(?i)^rb_type")
        .whitelist_type(r"(?i)^gplot")
        .whitelist_type(r"(?i)^jbdata")
        .whitelist_type(r"(?i)^jbclasser")
        .whitelist_type("DLLIST")
        .whitelist_type("DoubleLinkedList")
        .whitelist_function(r"^[df]?pix\w*")
        .whitelist_function(r"^box\w*")
        .whitelist_function(r"^l_\w*")
        .whitelist_function(r"^bbuffer\w*")
        .whitelist_function(r"^ccba\w*")
        .whitelist_function(r"^sela?\w*")
        .whitelist_function(r"^fopen\w+")
        .whitelist_function(r"^lept\w+")
        .whitelist_function(r"^lept_\w+")
        .whitelist_function(r"^sarray\w+")
        .whitelist_function(r"^ptraa?\w+")
        .whitelist_function(r"^ptaa?\w+")
        .whitelist_function(r"^numa*\w+")
        .whitelist_function(r"^dewarpa?\w+")
        .whitelist_function(r"^gplot\w+")
        .whitelist_function(r"^lheap\w+")
        .whitelist_function(r"^jb\w+")
        .whitelist_function(r"^kernel\w+")
        .whitelist_function(r"^pms\w+")
        .whitelist_function(r"^convert\w+")
        .whitelist_function(r"^generate\w+")
        .whitelist_function(r"^encode[\w\d]+")
        .whitelist_function(r"^decode[\w\d]+")
        .whitelist_var(r"^COLOR_\w+$")
        .whitelist_var(r"(?i)^l_\w+$")
        .blacklist_function("select")
        .header("wrapper.h")
        .header("extra/extraapi.h")
        .rustfmt_bindings(true);
    //.parse_callbacks(Box::new(bindgen::CargoCallbacks));

    if let Some(include_path) = opt_leptonica_include.as_ref() {
        bindgen_builder = bindgen_builder.clang_arg(format!("-I{}", include_path.display()));
    }

    bindgen_builder
        .generate()
        .expect("Can't generate Leptonica C Api bindings")
        .write_to_file(out_dir.join("bindings.rs"))
        .expect("Can't write Leptonica bindings");
}

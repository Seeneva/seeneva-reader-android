#[macro_use]
extern crate lazy_static;

use std::env;
use std::ffi::OsStr;
use std::fmt::Display;
use std::io::Result as IoResult;
use std::path::{Path, PathBuf};
use std::process::Command;

use bindgen;
use cmake;
use sysinfo::{ProcessExt, SystemExt};

use build_helper::*;

const ARCH_ARM: &str = "arm";
const ARCH_ARM64: &str = "aarch64";

lazy_static! {
    static ref OUT_DIR: PathBuf = PathBuf::from(&env::var("OUT_DIR").unwrap());
    static ref VENDOR_DIR: PathBuf =
        Path::new(&env::var("CARGO_MANIFEST_DIR").unwrap()).join("vendor");
    static ref DOWNLOADS_ROOT: PathBuf = VENDOR_DIR.join("downloads");
    static ref TF_ROOT: PathBuf = VENDOR_DIR.join("tensorflow");
    static ref TF_LITE_ROOT: PathBuf = TF_ROOT.join("tensorflow/lite");
}

//Ideally I want to get rid of using Makefile. But not for now.

// Do not link libpthread and librt to Android!
// https://developer.android.com/ndk/guides/stable_apis#c_library
// It is linked automatically

fn main() {
    println!("cargo:rerun-if-env-changed=CC");
    println!("cargo:rerun-if-env-changed=CXX");
    println!("cargo:rerun-if-env-changed=AR");
    println!("cargo:rerun-if-env-changed=CFLAGS");
    println!("cargo:rerun-if-env-changed=CXXFLAGS");

    prepare_tf_files().expect("Can't prepare Tensorflow Lite files");

    let mut make_cmd = Command::new_tf_make_base();

    let target = env::var("CARGO_CFG_TARGET_OS").unwrap();
    let target_arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap();

    make_cmd
        //build lib
        .arg(if cfg!(feature = "build_micro") {
            "micro"
        } else if cfg!(feature = "build_lib") {
            "lib"
        } else if cfg!(feature = "build_minimal") {
            "minimal"
        } else if cfg!(feature = "build_benchmark_lib") {
            "benchmark_lib"
        } else if cfg!(feature = "build_benchmark") {
            "benchmark"
        } else {
            "all"
        });

    // if cfg!(feature = "clean") {
    //     make_cmd.arg("cleantarget");
    // }

    make_cmd
        .arg(format!("TARGET={}", target))
        .arg(format!("TARGET_ARCH={}", target_arch))
        .arg(format!("DOWNLOADS_DIR={}", DOWNLOADS_ROOT.display()))
        .make_arg("OUT_DIR", None, false)
        //add linkers variables for cross compile
        .make_arg("CC", None, true)
        .make_arg("CXX", None, true)
        .make_arg("AR", None, true)
        .make_arg("CXXFLAGS", Some("EXTRA_CXXFLAGS"), true)
        //Enable "Neural Networks API" only on Android
        .arg(format!(
            "BUILD_WITH_NNAPI={}",
            cfg!(feature = "with_nnapi") && target == ANDROID
        ))
        .arg(format!("BUILD_WITH_GPU={}", cfg!(feature = "with_gpu")))
        .arg(format!("BUILD_WITH_MMAP={}", cfg!(feature = "with_mmap")))
        // enable RUY only on optimized architectures
        // see https://github.com/google/ruy/blob/master/README.md
        .arg(format!(
            "BUILD_WITH_RUY={}",
            cfg!(feature = "with_ruy") && (target_arch == ARCH_ARM || target_arch == ARCH_ARM64)
        ));

    {
        let extra_flags = env::var("CFLAGS");

        match env::var("PROFILE") {
            Ok(profile) if profile == "release" => {
                //add additional extra cflags
                if let Ok(flags) = extra_flags {
                    make_cmd.arg(format!("EXTRA_CFLAGS={}", flags));
                }
            }
            _ => {
                make_cmd.arg(format!(
                    "CFLAGS=-O0 -fPIC {}",
                    extra_flags.unwrap_or(String::new())
                ));
            }
        }
    }

    println!("Tensorflow Lite make: {:?}", make_cmd);

    //Run make command
    if !make_cmd.status().expect("Can't run 'make'").success() {
        panic!("Can't compile Tensorflow lite library. See output.");
    }

    ////path to static libraries output
    let lib_path = OUT_DIR
        .join("gen")
        .join(format!("{}_{}", target, target_arch))
        .join("lib");

    if !lib_path.exists() {
        panic!("Output directory doesn't exist.");
    }

    //add tensorflow lite library path to search list
    println!("cargo:rustc-link-search=native={}", lib_path.display());
    // cmake static libs path (ABSL, etc)
    println!(
        "cargo:rustc-link-search=native={}",
        OUT_DIR.join("lib").display()
    );

    //link static library libtensorflow-lite.a
    println!("cargo:rustc-link-lib=static=tensorflow-lite");

    if target == ANDROID {
        println!("cargo:rustc-link-lib=c++abi");
    }

    //we need to link to C++ library
    println!(
        "cargo:rustc-link-lib={}",
        get_cpp_link_stdlib(&target).expect("Can't determine c++ stdlib")
    );

    if cfg!(feature = "with_gpu") {
        //add required libs for GPU delegate

        println!("cargo:rustc-link-lib=static=absl_city");
        println!("cargo:rustc-link-lib=static=absl_hash");
        println!("cargo:rustc-link-lib=static=absl_graphcycles_internal");
        println!("cargo:rustc-link-lib=static=absl_synchronization");
        println!("cargo:rustc-link-lib=static=absl_hashtablez_sampler");
        println!("cargo:rustc-link-lib=static=absl_raw_hash_set");
        println!("cargo:rustc-link-lib=static=absl_stacktrace");
        println!("cargo:rustc-link-lib=static=absl_symbolize");

        println!("cargo:rustc-link-lib=GLESv2");
        println!("cargo:rustc-link-lib=EGL");
    }

    generate_c_api_bindings();
}

/// Prepare Tensorflow Lite files. Make some changes and put files into OUT_DIR
fn prepare_tf_files() -> IoResult<()> {
    {
        //download dependencies if didn't do it before
        if !DOWNLOADS_ROOT.exists() {
            println!("Download Tensorflow Lite dependencies");

            if !Command::new(Path::new("./vendor/download_dependencies.sh"))
                .status()?
                .success()
            {
                panic!("Can't execute download dependencies script");
            }
        }
    }

    // compile GPU FlatBuffer schemas
    if cfg!(feature = "with_gpu") {
        println!("Compile ABSL libs");

        cmake::Config::new(DOWNLOADS_ROOT.join("absl"))
            .generator("Unix Makefiles")
            .define("BUILD_TESTING", "OFF")
            .define("CMAKE_TRY_COMPILE_TARGET_TYPE", "STATIC_LIBRARY")
            .custom_target_define()
            .build();

        println!("Prepare GPU delegate FlatBuffer schemas");

        let gpu_delegate_path = TF_LITE_ROOT.join("delegates/gpu");

        flatc(
            gpu_delegate_path.join("cl"),
            Some(&["--scoped-enums"]),
            &["compiled_program_cache.fbs"],
        )?;

        flatc(
            gpu_delegate_path.join("gl"),
            Some(&["--scoped-enums"]),
            &["common.fbs", "compiled_model.fbs"],
        )?;

        flatc(
            gpu_delegate_path.join("gl"),
            None::<&[&str]>,
            &["workgroups.fbs", "metadata.fbs"],
        )?;
    }

    Ok(())
}

/// Call FlatBuffer schema compiler
fn flatc<D, A, AI, F, FI>(dir: D, args: Option<A>, files: F) -> IoResult<()>
where
    D: AsRef<Path>,
    A: IntoIterator<Item = AI>,
    AI: AsRef<std::ffi::OsStr>,
    F: IntoIterator<Item = FI>,
    FI: AsRef<std::ffi::OsStr>,
{
    let flatc_path = DOWNLOADS_ROOT.join("flatbuffers/flatc");

    // Compile flatbuffers binary to use it instead of any installed locally
    if !flatc_path.exists() {
        println!("Compile FlatBuffer");

        let system = {
            let mut system = sysinfo::System::new();

            system.refresh_processes();

            system
        };

        let host_envs = {
            let mut host_process = system.get_process(std::process::id() as _).unwrap();

            loop {
                let prev_host_process = host_process;

                host_process = host_process
                    .parent()
                    .and_then(|pid| system.get_process(pid))
                    .expect("Can't get host process");

                if prev_host_process.name() == "cargo" {
                    break;
                }
            }

            host_process
                .environ()
                .into_iter()
                .map(|e| {
                    let s = e.split("=").collect::<Vec<_>>();
                    (s[0], s[1])
                })
                .collect::<std::collections::HashMap<_, _>>()
        };

        // We need to compile flatc for HOST, not TARGET.
        // Maybe there is a better way but at 3 a.m. I can't think of a better way :)
        // So I remove all process' environment and set system environment afterwards

        let status = Command::new("cmake")
            .env_clear()
            .envs(&host_envs)
            .current_dir(DOWNLOADS_ROOT.join("flatbuffers"))
            .arg("-G")
            .arg("Unix Makefiles")
            .status()
            .and_then(|status| {
                if status.success() {
                    Command::new("make")
                        .env_clear()
                        .envs(host_envs)
                        .current_dir(DOWNLOADS_ROOT.join("flatbuffers"))
                        .status()
                } else {
                    Ok(status)
                }
            })?;

        if !status.success() {
            panic!("Can't build flatc. Status: {}", status);
        }
    }

    let mut cmd = Command::new(flatc_path);

    // we generate C++ headers
    cmd.arg("-c").current_dir(dir);

    if let Some(args) = args {
        cmd.args(args);
    }

    cmd.args(files);

    println!("FlatBuffer compiler command: {:?}", cmd);

    let res = cmd.status()?;

    if !res.success() {
        panic!("FlatBuffer command error code: {:?}", res.code());
    } else {
        return Ok(());
    }
}

fn generate_c_api_bindings() {
    let tf_lite_c_api_path = TF_LITE_ROOT.join("c");

    let bindings = bindgen::builder()
        .clang_arg(format!("-I{}", TF_ROOT.display()))
        .header(tf_lite_c_api_path.join("c_api.h").to_string_lossy())
        .header(tf_lite_c_api_path.join("common.h").to_string_lossy())
        //To enable GPU support
        .header(
            TF_LITE_ROOT
                .join("delegates/gpu/delegate.h")
                .to_string_lossy(),
        )
        .rustfmt_bindings(true)
        .parse_callbacks(Box::new(bindgen::CargoCallbacks))
        .generate()
        .expect("Can't generate TF Lite C Api bindings");

    bindings
        .write_to_file(OUT_DIR.join("bindings.rs"))
        .expect("Can't write TF Lite C Api bindings");
}

trait CommandExt {
    fn new_tf_make_base() -> Self;

    /// Add make variables from env variables
    fn make_arg<K>(&mut self, env_key: K, arg_key: Option<K>, optional: bool) -> &mut Self
    where
        K: AsRef<OsStr> + Display;
}

impl CommandExt for Command {
    /// Create a new TF Lite make command
    fn new_tf_make_base() -> Self {
        let mut cmd = Command::new("make");

        //allow override using env variables
        //make_cmd.arg("-e")

        if let Ok(num_jobs) = env::var("NUM_JOBS") {
            cmd.arg("-j").arg(num_jobs);
        }

        cmd.arg("-f")
            .arg(Path::new("../Makefile"))
            //we need to change working dir before running Makefile
            .current_dir(TF_ROOT.as_path());

        cmd
    }

    /// add make arguments from env variables. If [arg_key] is None than [env_key] will be used
    fn make_arg<K>(&mut self, env_key: K, arg_key: Option<K>, optional: bool) -> &mut Self
    where
        K: AsRef<OsStr> + Display,
    {
        let arg_key = match arg_key.as_ref() {
            Some(arg_key) => arg_key,
            _ => &env_key,
        };

        match env::var(&env_key) {
            Ok(var) => self.arg(format!("{}={}", arg_key, var)),
            _ => {
                if optional {
                    self
                } else {
                    panic!("Can't get env variable {}", env_key);
                }
            }
        }
    }
}

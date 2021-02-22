use std::env;

pub const ANDROID: &str = "android";

//From https://docs.rs/cc/
/// Returns the default C++ standard library for the current target: `libc++`
/// for OS X and `libstdc++` for anything else.
pub fn get_cpp_link_stdlib(target: &str) -> Option<String> {
    if let Ok(stdlib) = env::var("CXXSTDLIB") {
        if stdlib.is_empty() {
            None
        } else {
            Some(stdlib)
        }
    } else {
        if target.contains("msvc") {
            None
        } else if target.contains("apple") {
            Some("c++".to_string())
        } else if target.contains("freebsd") {
            Some("c++".to_string())
        } else if target.contains("openbsd") {
            Some("c++".to_string())
        } else if target.contains(ANDROID) {
            Some("c++_shared".to_string())
        } else {
            Some("stdc++".to_string())
        }
    }
}

pub trait CmakeExt {
    /// Add target defines
    fn custom_target_define(&mut self) -> &mut Self;
}

impl CmakeExt for cmake::Config {
    fn custom_target_define(&mut self) -> &mut Self {
        let target = env::var("CARGO_CFG_TARGET_OS").unwrap();

        match target.as_str() {
            ANDROID => {
                for env_key in &[
                    "CMAKE_TOOLCHAIN_FILE",
                    "ANDROID_ABI",
                    "ANDROID_NATIVE_API_LEVEL",
                ] {
                    if let Ok(env_val) = env::var(env_key) {
                        self.define(env_key, env_val);
                    }
                }

                self
            }
            _ => self,
        }
    }
}

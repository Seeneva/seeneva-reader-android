#[macro_use]
extern crate log;

#[cfg(target_os = "android")]
mod android;
mod comics;
mod task;
mod utils;

#[cfg(test)]
pub mod tests {
    use std::env;
    use std::path::PathBuf;

    ///Base path to all test data
    pub fn base_test_path() -> PathBuf {
        if cfg!(target_os = "android") {
            PathBuf::from("/data/local/tmp/comics_test")
        } else {
            let mut path = env::current_dir().unwrap();
            path.push("test");
            path
        }
    }
}

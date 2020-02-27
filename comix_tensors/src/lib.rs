#[macro_use]
extern crate futures;
#[macro_use]
extern crate log;

pub use future_runtime::{executor as future_executor, init as init_future_runtime};
pub use io::fd::FileRawFd;

use self::comics::prelude::*;

#[cfg(target_os = "android")]
mod android;
mod comics;
mod future_runtime;
mod io;
//mod ml;

//#[cfg(test)]
//pub mod tests {
//    use std::env;
//    use std::path::PathBuf;
//
//    use super::*;
//
//    #[test]
//    fn test_get_comic_pages_objects() {
//        use ndarray::prelude::*;
//        use std::fs::File;
//        use std::io::Read;
//
//        let mut buf = Vec::new();
//
//        let cfg = {
//            let mut p: PathBuf = base_test_path();
//            p.push("com.almadevelop.comix.dat");
//
//            File::open(p).unwrap().read_to_end(&mut buf);
//
//            parse_config(&buf)
//        };
//
//        let count = cfg.image().batchSize();
//
//        let comic_pages_object = {
//            let mut positions = vec![];
//            let mut names = vec![];
//
//            for i in 0..count {
//                positions.push(i as usize);
//                names.push(format!("File_{}", i));
//            }
//
//            let interpreter_out = InterpreterOutput {
//                positions,
//                names,
//                content: Array3::zeros(cfg.interpreter_output_shape()),
//            };
//
//            get_comic_pages_objects(interpreter_out, &cfg)
//        };
//
//        assert_eq!(comic_pages_object.len(), count as usize);
//
//        for (i, object) in comic_pages_object.into_iter().enumerate() {
//            assert_eq!(object.page_position, i as usize);
//            assert_eq!(object.page_name, format!("File_{}", i));
//        }
//    }
//
//    ///Base path to all test data
//    pub fn base_test_path() -> PathBuf {
//        if cfg!(target_os = "android") {
//            PathBuf::from("/data/local/tmp/comics_test")
//        } else {
//            [env::current_dir().unwrap().to_str().unwrap(), "test"]
//                .iter()
//                .collect::<PathBuf>()
//        }
//    }
//}

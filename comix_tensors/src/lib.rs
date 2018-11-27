mod futures_ext;
mod comics;
mod ml;
#[cfg(target_os = "android")]
mod android;

#[macro_use]
extern crate log;
#[macro_use]
extern crate itertools;
#[macro_use]
extern crate lazy_static;

use self::ml::*;
use self::comics::prelude::*;

#[cfg(test)]
pub mod tests {
    use super::*;
    use std::env;
    use std::path::PathBuf;

    #[test]
    fn test_get_comic_pages_objects(){
        use std::io::Read;
        use ndarray::prelude::*;
        use std::fs::File;

        let mut buf = Vec::new();

        let cfg = {
            let mut p: PathBuf = base_test_path();
            p.push("com.almadevelop.comix.dat");

            File::open(p).unwrap().read_to_end(&mut buf);

            parse_config(&buf)
        };

        let count = cfg.image().batchSize();

        let comic_pages_object = {
            let mut positions = vec![];
            let mut names = vec![];

            for i in 0..count {
                positions.push(i as usize);
                names.push(format!("File_{}", i));
            }

            let interpreter_out = InterpreterOutput {
                positions,
                names,
                content: Array3::zeros(cfg.interpreter_output_shape())
            };

            get_comic_pages_objects(interpreter_out, &cfg)
        };

        assert_eq!(comic_pages_object.len(), count as usize);

        for (i, object) in comic_pages_object.into_iter().enumerate(){
            assert_eq!(object.page_position, i as usize);
            assert_eq!(object.page_name, format!("File_{}", i));
        }
    }

    ///Base path to all test data
    pub fn base_test_path() -> PathBuf {
        if cfg!(target_os = "android") {
            PathBuf::from("/data/local/tmp/test")
        } else {
            [env::current_dir().unwrap().to_str().unwrap(), "test"]
                .iter()
                .collect::<PathBuf>()
        }
    }
}

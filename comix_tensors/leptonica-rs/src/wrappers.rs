use std::ffi::CString;
use std::path::Path;

use leptonica_sys;
use libc;

/// Wrapper around C FILE pointer
#[derive(Debug)]
pub(crate) struct File(*mut libc::FILE);

impl File {
    #[track_caller]
    pub fn new(path: impl AsRef<Path>, mode: &str) -> Self {
        let path =
            crate::helper::path_to_cstring(path.as_ref()).expect("Can't convert path to C string");

        let mode = CString::new(mode).expect("Can't get mode");

        let file_ptr = unsafe { leptonica_sys::lept_fopen(path.as_ptr() as _, mode.as_ptr()) };

        if file_ptr.is_null() {
            panic!("Can't open C FILE. Pointer is null");
        }

        File(file_ptr)
    }

    pub fn as_ptr(&self) -> *mut libc::FILE {
        self.0
    }
}

impl Drop for File {
    fn drop(&mut self) {
        unsafe {
            if leptonica_sys::lept_fclose(self.0) != 0 {
                panic!("Can't close C file")
            }
        }
    }
}

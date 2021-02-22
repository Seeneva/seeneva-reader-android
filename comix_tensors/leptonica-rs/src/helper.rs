use std::ffi::CString;
use std::path::Path;

#[track_caller]
pub(crate) fn path_to_cstring(path: &Path) -> Result<CString, std::ffi::NulError> {
    if cfg!(unix) {
        use std::os::unix::ffi::OsStrExt;
        CString::new(path.as_os_str().as_bytes())
    } else {
        CString::new(path.as_os_str().to_str().expect("Can't get path"))
    }
}

use std::borrow::Cow;
use std::ffi::CStr;
use std::ops::{Deref, DerefMut};
use std::ptr;
use std::slice;

use num_traits::FromPrimitive;
use tokio::prelude::*;
use unrar_rs as rar;
use unrar_rs::{CallbackMessages, ProcessOperation, HANDLE};
pub use unrar_rs::{OpenMode, OpenResult};

use crate::FileRawFd;

use super::{ArchiveFile, ComicContainer, ComicContainerError, ComicFilesStream, FindFileFuture};

use self::error::*;

mod error;

pub type RarResult<T> = ::std::result::Result<T, RarError>;

///Contain archive file data
#[derive(Debug)]
pub struct RarArchive {
    fd: FileRawFd,
}

impl RarArchive {
    pub fn new(fd: FileRawFd) -> Self {
        RarArchive { fd }
    }

    fn open(&self) -> impl Future<Item = ArchiveHandle, Error = RarError> {
        future::result(self.fd.dup())
            .map_err(Into::into)
            .and_then(|fd| open_archive(fd, OpenMode::RAR_OM_EXTRACT).map(|(_, handle)| handle))
    }

    fn stream_files(&self) -> impl Stream<Item = ArchiveFile, Error = RarError> {
        self.open().map(stream::iter_result).flatten_stream()
    }

    fn file_by_position(
        &self,
        pos: usize,
    ) -> impl Future<Item = Option<ArchiveFile>, Error = RarError> {
        self.open().and_then(move |handle| handle.by_position(pos))
    }
}

impl ComicContainer for RarArchive {
    fn files(&self) -> ComicFilesStream {
        Box::new(self.stream_files().from_err())
    }

    fn file_by_position(&self, pos: usize) -> FindFileFuture {
        Box::new(self.file_by_position(pos).from_err())
    }
}

///Open archive and get [HANDLE]
fn open_archive(fd: FileRawFd, mode: OpenMode) -> RarResult<(rar::OpenArchiveData, ArchiveHandle)> {
    let fd = fd.into_fd();

    debug!("Trying to open RAR archive {}", fd);

    let mut archive = rar::OpenArchiveData::new(fd, None, mode);

    let handle = unsafe { rar::RAROpenArchive(&mut archive) };

    if !archive.OpenResult.is_ok() {
        error!(
            "Can't open RAR archive {}. Result: {:?}",
            fd, archive.OpenResult
        );
        return Err(archive.OpenResult.into());
    }

    debug!("RAR archive {} opened", fd);

    Ok((archive, ArchiveHandle::from(handle)))
}

///Wrapper. Used to get file information
#[derive(Default, Clone)]
struct HeaderData(rar::RARHeaderData);

impl Deref for HeaderData {
    type Target = rar::RARHeaderData;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for HeaderData {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl From<rar::RARHeaderData> for HeaderData {
    fn from(header_data: rar::RARHeaderData) -> Self {
        HeaderData(header_data)
    }
}

impl HeaderData {
    ///Read file name
    pub fn file_name(&self) -> Cow<str> {
        unsafe { CStr::from_ptr(&self.FileName as *const _).to_string_lossy() }
    }

    ///Is current file is a directory. If so you cant call [read_content] it will cause error [RarError::ReadDir]
    pub fn is_dir(&self) -> bool {
        self.Flags & (rar::RHDF_DIRECTORY as u32) != 0
    }
}

///Wrapper around RAR HANDLE. Used to process RAR archive files
#[derive(Debug, Clone)]
struct ArchiveHandle(HANDLE);

unsafe impl Send for ArchiveHandle {}

impl From<HANDLE> for ArchiveHandle {
    fn from(handle: HANDLE) -> Self {
        ArchiveHandle(handle)
    }
}

impl ArchiveHandle {
    ///Read current file header.
    /// Return [Ok(false)] if there is no more file to read
    fn read_header(&self, header_data: &mut rar::RARHeaderData) -> RarResult<bool> {
        let res = unsafe { rar::RARReadHeader(self.0, header_data) };

        //if OpenResult::ERAR_END_ARCHIVE it is the end
        match res {
            OpenResult::ERAR_SUCCESS => Ok(true),
            OpenResult::ERAR_END_ARCHIVE => Ok(false),
            _ => {
                error!("Error occured while read RAR file header. Error: {:?}", res);
                Err(res.into())
            }
        }
    }

    ///Proceed operation and seek to the next file
    fn process_file(&self, op: ProcessOperation) -> RarResult<()> {
        let res = unsafe { rar::RARProcessFile(self.0, op as _, ptr::null_mut(), ptr::null_mut()) };

        if !res.is_ok() {
            error!("Error occured while process RAR file. Error {:?}", res);
            return Err(res.into());
        }

        Ok(())
    }

    ///Read file content. Return file bytes
    fn read_file(&self) -> RarResult<Vec<u8>> {
        let mut buffer = Vec::<u8>::new();

        unsafe { rar::RARSetCallback(self.0, Self::callback, &mut buffer as *mut _ as _) };

        self.process_file(ProcessOperation::RAR_TEST)?;

        Ok(buffer)
    }

    //    ///Read header and move to the next file
    //    fn move_next(&self, header_data: &mut rar::RARHeaderData) -> RarResult<bool> {
    //        self.process_file(ProcessOperation::RAR_SKIP)?;
    //
    //        self.read_header(header_data)
    //    }

    ///Find and extract file by it position [pos]
    /// Return [Ok(None)] if can't find requested file
    fn by_position(self, pos: usize) -> RarResult<Option<ArchiveFile>> {
        let mut header_data = HeaderData::default();

        let mut archive_pos = 0usize;

        loop {
            if !self.read_header(&mut header_data)? {
                info!("Can't find RAR archive file by position {}", pos);
                return Ok(None);
            }

            if archive_pos == pos {
                return self.read_file().map(|file_content| {
                    Some(into_archive_file(&header_data, Some(file_content), pos))
                });
            } else {
                self.process_file(ProcessOperation::RAR_SKIP)?;
            }

            archive_pos += 1;
        }
    }

    unsafe extern "C" fn callback(
        msg: rar::c_uint,
        user_data: rar::c_long,
        p1: rar::c_long,
        p2: rar::c_long,
    ) -> rar::c_int {
        match CallbackMessages::from_u32(msg)
            .expect("RAR callback message should be one of accepted by CallbackMessages")
        {
            CallbackMessages::UCM_PROCESSDATA => {
                let file = p1 as *const u8;
                let size = p2 as usize;
                let data = user_data as *mut Vec<u8>;

                let dict_buffer = slice::from_raw_parts(file, size);

                (*data).extend_from_slice(dict_buffer);

                0
            }
            _ => -1,
        }
    }
}

impl IntoIterator for ArchiveHandle {
    type Item = RarResult<ArchiveFile>;
    type IntoIter = ArchiveIterator;

    fn into_iter(self) -> Self::IntoIter {
        ArchiveIterator {
            archive: self,
            header_data: HeaderData::default(),
            current_pos: 0,
        }
    }
}

impl Drop for ArchiveHandle {
    fn drop(&mut self) {
        let rewind_result;
        let close_result: OpenResult;

        unsafe {
            rewind_result = rar::RarRewind(self.0);
            close_result = rar::RARCloseArchive(self.0);
        }

        if !close_result.is_ok() {
            panic!(
                "Error ocurred while trying to drop RAR handle. Result {:?}",
                close_result
            );
        }

        if !rewind_result {
            panic!("Error occurred while trying to rewind RAR handle");
        }
    }
}

#[derive(Clone)]
struct ArchiveIterator {
    archive: ArchiveHandle,
    header_data: HeaderData,
    current_pos: usize,
}

unsafe impl Send for ArchiveIterator {}

impl Iterator for ArchiveIterator {
    type Item = RarResult<ArchiveFile>;

    fn next(&mut self) -> Option<Self::Item> {
        let res = match self.archive.read_header(&mut self.header_data) {
            Ok(ref has_next) if !has_next => None,
            Err(e) => Some(Err(e)),
            Ok(_) => {
                debug!("Proceed RAR file {}", self.header_data.file_name());
                Some(
                    if self.header_data.is_dir() {
                        self.archive
                            .process_file(ProcessOperation::RAR_SKIP)
                            .map(|_| None)
                    } else {
                        self.archive.read_file().map(|buf| Some(buf))
                    }
                    .map(|file_content| {
                        into_archive_file(&self.header_data, file_content, self.current_pos)
                    }),
                )
            }
        };

        self.current_pos += 1;

        res
    }
}

fn into_archive_file(
    header_data: &HeaderData,
    content: Option<Vec<u8>>,
    pos: usize,
) -> ArchiveFile {
    if !header_data.is_dir() && content.is_none() {
        panic!(
            "Cannot convert RAR header {:?}. Buffer is empty",
            header_data.file_name()
        );
    }

    ArchiveFile {
        pos,
        name: header_data.file_name().into_owned(),
        is_dir: header_data.is_dir(),
        content,
    }
}

#[cfg(test)]
#[cfg(target_family = "unix")]
mod tests {
    use std::fs::File;
    use std::os::unix::io::FromRawFd;

    //    use crate::comics::archive::tests::open_archive_fd;
    //    use crate::comics::magic::{resolve_file_magic_type, MagicType};
    //
    //    use super::*;

    //    #[test]
    //    fn test_stream_cbr_archive() {
    //        let fd = open_rar_fd();
    //        let rar_archive = RarArchive::new(fd);
    //
    //        let mut file_count = 0u32;
    //
    //        rar_archive.stream_files().wait().for_each(|file| {
    //            let file = file.unwrap();
    //            assert_eq!(
    //                file.content.is_none(),
    //                file.is_dir,
    //                "If it's a file it should contain content. Otherwise it should be empty"
    //            );
    //
    //            assert_eq!(
    //                file.is_dir,
    //                file.name == "image_folder",
    //                "Wrong dir detection {}",
    //                file.name
    //            );
    //
    //            file_count += 1;
    //        });
    //
    //        assert_eq!(
    //            file_count, 11,
    //            "Wrong number of RAR archive files. Count {}",
    //            file_count
    //        );
    //
    //        close_rar_fd(fd);
    //    }
    //
    //    #[test]
    //    fn test_guess_cbr_magic_type() {
    //        let fd = open_rar_fd();
    //        let mut file = unsafe { File::from_raw_fd(fd) };
    //
    //        let res = resolve_file_magic_type(&mut file).unwrap();
    //        assert_eq!(res, MagicType::RAR);
    //    }
    //
    //    #[test]
    //    fn test_find_file_success() {
    //        let fd = open_rar_fd();
    //
    //        let file = RarArchive::new(fd)
    //            .file_by_position(3)
    //            .wait()
    //            .unwrap()
    //            .unwrap();
    //
    //        assert_eq!(file.name, "pexels-photo-1058770.jpeg", "Wrong file name");
    //    }
    //
    //    #[test]
    //    fn test_find_file_empty() {
    //        let fd = open_rar_fd();
    //
    //        let file = RarArchive::new(fd).file_by_position(100).wait().unwrap();
    //
    //        assert!(file.is_none());
    //    }
    //
    //    fn open_rar_fd() -> RawFd {
    //        open_archive_fd(&["rar", "comics_test.cbr"])
    //    }
    //
    //    fn close_rar_fd(fd: RawFd) {
    //        let res = unsafe { libc::close(fd) };
    //
    //        assert_eq!(res, 0, "Cant close file descriptor: {}", res);
    //    }
}

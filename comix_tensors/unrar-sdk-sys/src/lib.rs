#![allow(non_snake_case, non_camel_case_types)]

use libc;
use num_derive::*;
use num_traits::{FromPrimitive, ToPrimitive};
use std::borrow::Cow;
use std::ffi::{CStr, CString};
use std::ptr;

pub type c_char = libc::c_char;
pub type c_uchar = libc::c_uchar;
pub type c_uint = libc::c_uint;
pub type c_int = libc::c_int;
pub type c_long = libc::c_long;

pub type HANDLE = *const libc::c_void;

pub type CALLBACK =
    unsafe extern "C" fn(msg: c_uint, UserData: c_long, P1: c_long, P2: c_long) -> c_int;

pub type PROCESSDATAPROC = unsafe extern "C" fn(Addr: *mut c_uchar, Size: c_int) -> c_int;

pub const RHDF_SPLITBEFORE: u8 = 0x01;
pub const RHDF_SPLITAFTER: u8 = 0x02;
pub const RHDF_ENCRYPTED: u8 = 0x04;
pub const RHDF_SOLID: u8 = 0x10;
pub const RHDF_DIRECTORY: u8 = 0x20;

#[repr(u32)]
#[derive(Debug, Copy, Clone, Eq, PartialEq, FromPrimitive, ToPrimitive)]
pub enum OpenMode {
    ///Open archive for reading file headers only.
    RAR_OM_LIST = 0,
    ///Open archive for testing and extracting files.
    RAR_OM_EXTRACT = 1,
    ///   Open archive for reading file headers only. If you open an archive in such mode,
    ///RARReadHeaderEx will return all file headers, including those with
    ///“file continued from previous volume” flag. In case of constants.RAR_OM_LIST
    ///such headers are automatically skipped.
    ///So if you process RAR volumes in constants.RAR_OM_LIST_INCSPLIT mode, you will get several file
    ///header records for same file if file is split between volumes.
    ///For such files only the last file header record will contain the correct file CRC a
    ///nd if you wish to get the correct packed size, you need to sum up packed sizes of all parts.
    RAR_OM_LIST_INCSPLIT = 2,
}

#[repr(C)]
#[derive(Debug, Copy, Clone, Eq, PartialEq, FromPrimitive, ToPrimitive)]
pub enum OpenResult {
    ERAR_SUCCESS = 0,
    ERAR_END_ARCHIVE = 10,
    ///Not enough memory to initialize data structures
    ERAR_NO_MEMORY = 11,
    /// Archive header broken
    ERAR_BAD_DATA = 12,
    ///File is not valid RAR archive
    ERAR_BAD_ARCHIVE = 13,
    /// Unknown encryption used for archive headers
    ERAR_UNKNOWN_FORMAT = 14,
    ///File open error
    ERAR_EOPEN = 15,
    ERAR_ECREATE = 16,
    ERAR_ECLOSE = 17,
    ERAR_EREAD = 18,
    ERAR_EWRITE = 19,
    ERAR_SMALL_BUF = 20,
    ERAR_UNKNOWN = 21,
    ERAR_MISSING_PASSWORD = 22,
    ERAR_EREFERENCE = 23,
    ERAR_BAD_PASSWORD = 24,
}

impl OpenResult {
    pub fn is_ok(&self) -> bool {
        *self == OpenResult::ERAR_SUCCESS
    }
}

#[repr(u32)]
#[derive(Debug, Copy, Clone, Eq, PartialEq, FromPrimitive, ToPrimitive)]
pub enum ArchiveDataFlags {
    ROADF_VOLUME = 0x0001,
    ROADF_COMMENT = 0x0002,
    ROADF_LOCK = 0x0004,
    ROADF_SOLID = 0x0008,
    ROADF_NEWNUMBERING = 0x0010,
    ROADF_SIGNED = 0x0020,
    ROADF_RECOVERY = 0x0040,
    ROADF_ENCHEADERS = 0x0080,
    ROADF_FIRSTVOLUME = 0x0100,
}

#[repr(u32)]
#[derive(Debug, Copy, Clone, Eq, PartialEq, FromPrimitive, ToPrimitive)]
pub enum ProcessOperation {
    ///Move to the next file in the archive.
    /// If the archive is solid and constants.
    /// RAR_OM_EXTRACT mode was set when the archive was opened,
    /// the current file will be processed - the operation will be performed slower than a simple seek.
    RAR_SKIP = 0,
    ///Test the current file and move to the next file in the archive.
    /// If the archive was opened with constants.RAR_OM_LIST mode, the operation is equal to constants.RAR_SKIP.
    RAR_TEST = 1,
    ///Extract the current file and move to the next file.
    /// If the archive was opened with constants.RAR_OM_LIST mode, the operation is equal to constants.RAR_SKIP.
    RAR_EXTRACT = 2,
}

#[repr(u32)]
#[derive(Debug, Copy, Clone, Eq, PartialEq, FromPrimitive, ToPrimitive)]
pub enum CallbackMessages {
    UCM_CHANGEVOLUME = 0,
    UCM_PROCESSDATA = 1,
    UCM_NEEDPASSWORD = 2,
    UCM_CHANGEVOLUMEW = 3,
    UCM_NEEDPASSWORDW = 4,
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct OpenArchiveData {
    pub ArcName: *const c_char,
    pub Fd: c_int,
    pub OpenMode: c_uint,
    pub OpenResult: OpenResult,
    pub CmtBuf: *mut c_char,
    pub CmtBufSize: c_uint,
    pub CmtSize: c_uint,
    pub CmtState: c_uint,
}

impl OpenArchiveData {
    pub fn new(archive_fd: c_int, archive_name: Option<&str>, mode: OpenMode) -> Self {
        let archive_name = archive_name.unwrap_or("comics.cbr");
        let archive_name = CString::new(archive_name).unwrap();

        OpenArchiveData {
            ArcName: archive_name.as_ptr(),
            Fd: archive_fd,
            OpenMode: mode as u32,
            OpenResult: OpenResult::ERAR_SUCCESS,
            CmtBuf: ptr::null_mut(),
            CmtBufSize: 0,
            CmtSize: 0,
            CmtState: 0,
        }
    }
}

#[repr(C)]
#[derive(Copy, Clone)]
pub struct RARHeaderData {
    pub ArcName: [c_char; 260],
    pub FileName: [c_char; 260],
    pub Flags: c_uint,
    pub PackSize: c_uint,
    pub UnpSize: c_uint,
    pub HostOS: c_uint,
    pub FileCRC: c_uint,
    pub FileTime: c_uint,
    pub UnpVer: c_uint,
    pub Method: c_uint,
    pub FileAttr: c_uint,
    pub CmtBuf: *mut c_char,
    pub CmtBufSize: c_uint,
    pub CmtSize: c_uint,
    pub CmtState: c_uint,
}

impl Default for RARHeaderData {
    fn default() -> Self {
        RARHeaderData {
            ArcName: [0; 260],
            FileName: [0; 260],
            CmtBuf: ptr::null_mut(),
            Flags: 0,
            PackSize: 0,
            UnpSize: 0,
            HostOS: 0,
            FileCRC: 0,
            FileTime: 0,
            UnpVer: 0,
            Method: 0,
            FileAttr: 0,
            CmtBufSize: 0,
            CmtSize: 0,
            CmtState: 0,
        }
    }
}

#[link(name = "unrar")]
extern "C" {
    ///Open RAR archive and allocate memory structures (about 1 Mb)
    pub fn RAROpenArchive(data: *mut OpenArchiveData) -> HANDLE;
    /// Close RAR archive and release allocated memory. It must be called when
    ///  archive processing is finished, even if the archive processing was stopped
    ///  due to an error.
    pub fn RARCloseArchive(hArcData: HANDLE) -> OpenResult;
    ///Read header of file in archive.
    pub fn RARReadHeader(hArcData: HANDLE, HeaderData: *mut RARHeaderData) -> OpenResult;
    ///Performs action and moves the current position in the archive to the next file.
    /// Extract or test the current file from the archive opened in constants.RAR_OM_EXTRACT mode.
    /// If the mode constants.RAR_OM_LIST is set, then a call to this function will simply skip the archive position to the next file.
    pub fn RARProcessFile(
        hArcData: HANDLE,
        Operation: c_int,
        DestPath: *mut c_char,
        DestName: *mut c_char,
    ) -> OpenResult;
    ///Set a user-defined callback function to process unrar events.
    pub fn RARSetCallback(hArcData: HANDLE, Callback: CALLBACK, UserData: c_long);
    pub fn RARSetProcessDataProc(hArcData: HANDLE, ProcessDataProc: PROCESSDATAPROC);

    pub fn ReadCurrentComicFile(hArcData: HANDLE, Operation: c_int) -> c_int;
    pub fn RarRewind(hArcData: HANDLE) -> bool;
}

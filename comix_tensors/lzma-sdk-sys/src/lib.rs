#![allow(non_snake_case, non_camel_case_types)]

use libc;
use std::error::Error;
use std::mem;
use std::ptr;
use std::slice;
use std::string::String;

pub type WRes = libc::c_int;
pub type Sres = libc::c_int;
pub type ISzAllocPtr = *mut ISzAlloc;

pub type Byte = libc::c_uchar;
pub type UInt16 = libc::c_ushort;
pub type UInt64 = libc::c_ulonglong;
pub type UInt32 = libc::c_uint;
pub type Int64 = libc::c_longlong;

pub type c_void = libc::c_void;
pub type c_int = libc::c_int;
pub type size_t = libc::size_t;
pub type c_char = libc::c_char;

pub type FILE = libc::FILE;

#[repr(C)]
#[derive(Debug, PartialEq, Copy, Clone)]
pub enum SZ {
    SZ_OK = 0,

    SZ_ERROR_DATA = 1,
    SZ_ERROR_MEM = 2,
    SZ_ERROR_CRC = 3,
    SZ_ERROR_UNSUPPORTED = 4,
    SZ_ERROR_PARAM = 5,
    SZ_ERROR_INPUT_EOF = 6,
    SZ_ERROR_OUTPUT_EOF = 7,
    SZ_ERROR_READ = 8,
    SZ_ERROR_WRITE = 9,
    SZ_ERROR_PROGRESS = 10,
    SZ_ERROR_FAIL = 11,
    SZ_ERROR_THREAD = 12,
    SZ_ERROR_ARCHIVE = 16,
    SZ_ERROR_NO_ARCHIVE = 17,
}

impl SZ {
    pub fn is_ok(&self) -> bool {
        *self == SZ::SZ_OK
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct ISzAlloc {
    pub Alloc: unsafe extern "C" fn(ISzAllocPtr, size_t) -> *mut c_void,
    pub Free: unsafe extern "C" fn(ISzAllocPtr, *mut c_void),
}

impl ISzAlloc {
    pub fn g_alloc() -> Self {
        ISzAlloc {
            Alloc: SzAlloc,
            Free: SzFree,
        }
    }

    pub fn alloc(&mut self, size: size_t) -> *mut c_void {
        let alloc = self.Alloc;
        unsafe { alloc(self, size) }
    }

    pub fn free(&mut self, buf: *mut c_void) {
        let free = self.Free;
        unsafe { free(self, buf) }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub enum ESzSeek {
    SeekSet = 0,
    SeekCur = 1,
    SeekEnd = 2,
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CSzFile {
    pub file: *mut FILE,
}

impl Default for CSzFile {
    fn default() -> Self {
        CSzFile {
            file: ptr::null_mut(),
        }
    }
}

#[repr(C)]
#[derive(Debug, Default, Copy, Clone)]
pub struct CFileInStream {
    pub vt: ISeekInStream,
    pub file: CSzFile,
}

impl From<*mut FILE> for CFileInStream {
    fn from(file: *mut FILE) -> Self {
        CFileInStream {
            vt: ISeekInStream::default(),
            file: CSzFile { file },
        }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct ISeekInStream {
    pub Read:
        *mut extern "C" fn(p: *const ISeekInStream, buf: *mut c_void, size: *mut size_t) -> Sres,
    pub Seek: *mut extern "C" fn(p: *const ISeekInStream, pos: *mut Int64, origin: ESzSeek) -> Sres,
}

impl Default for ISeekInStream {
    fn default() -> Self {
        ISeekInStream {
            Read: ptr::null_mut(),
            Seek: ptr::null_mut(),
        }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CLookToRead2 {
    pub vt: ILookInStream,
    pub realStream: *const ISeekInStream,

    pub pos: size_t,
    pub size: size_t, // it's data size

    pub buf: *mut Byte, //the following variables must be set outside
    pub bufSize: size_t,
}

impl Default for CLookToRead2 {
    fn default() -> Self {
        CLookToRead2 {
            vt: Default::default(),
            realStream: ptr::null(),
            pos: 0,
            size: 0,
            buf: ptr::null_mut(),
            bufSize: 0,
        }
    }
}

impl CLookToRead2 {
    pub fn init(&mut self) {
        self.pos = 0;
        self.size = 0;
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct ILookInStream {
    pub Look: *mut extern "C" fn(
        p: *const ILookInStream,
        buf: *const *const c_void,
        size: *mut size_t,
    ) -> Sres,
    ///if (input(*size) != 0 && output(*size) == 0) means end_of_stream.
    /// (output(*size) > input(*size)) is not allowed
    ///  (output(*size) < input(*size)) is allowed
    pub Skip: *mut extern "C" fn(p: *const ILookInStream, offset: size_t) -> Sres,
    ///offset must be <= output(*size) of Look
    pub Read:
        *mut extern "C" fn(p: *const ILookInStream, buf: *mut c_void, size: *mut size_t) -> Sres,
    ///eads directly (without buffer). It's same as ISeqInStream::Read
    pub Seek: *mut extern "C" fn(p: *const ILookInStream, pos: *mut Int64, origin: ESzSeek) -> Sres,
}

impl Default for ILookInStream {
    fn default() -> Self {
        ILookInStream {
            Look: ptr::null_mut(),
            Skip: ptr::null_mut(),
            Read: ptr::null_mut(),
            Seek: ptr::null_mut(),
        }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CSzAr {
    pub NumPackStreams: UInt32,
    pub NumFolders: UInt32,

    pub PackPositions: *mut UInt64, // NumPackStreams + 1
    pub FolderCRCs: CSzBitUi32s,    // NumFolders

    pub FoCodersOffsets: *mut size_t,        //NumFolders + 1
    pub FoStartPackStreamIndex: *mut UInt32, // NumFolders + 1
    pub FoToCoderUnpackSizes: *mut UInt32,   //NumFolders + 1
    pub FoToMainUnpackSizeIndex: *mut Byte,  // NumFolders
    pub CoderUnpackSizes: *mut UInt64,       // for all coders in all folders

    pub CodersData: *mut Byte,
}

impl Default for CSzAr {
    fn default() -> Self {
        CSzAr {
            NumPackStreams: 0,
            NumFolders: 0,
            PackPositions: ptr::null_mut(),
            FolderCRCs: Default::default(),
            FoCodersOffsets: ptr::null_mut(),
            FoStartPackStreamIndex: ptr::null_mut(),
            FoToCoderUnpackSizes: ptr::null_mut(),
            FoToMainUnpackSizeIndex: ptr::null_mut(),
            CoderUnpackSizes: ptr::null_mut(),
            CodersData: ptr::null_mut(),
        }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CSzBitUi32s {
    pub Defs: *mut Byte, //MSB 0 bit numbering
    pub Vals: *mut UInt32,
}

impl Default for CSzBitUi32s {
    fn default() -> Self {
        CSzBitUi32s {
            Defs: ptr::null_mut(),
            Vals: ptr::null_mut(),
        }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CNtfsFileTime {
    pub Low: UInt32,
    pub High: UInt32,
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CSzBitUi64s {
    pub Defs: *mut Byte, //MSB 0 bit numbering
    pub Vals: *mut CNtfsFileTime,
}

impl Default for CSzBitUi64s {
    fn default() -> Self {
        CSzBitUi64s {
            Defs: ptr::null_mut(),
            Vals: ptr::null_mut(),
        }
    }
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
pub struct CSzArEx {
    pub db: CSzAr,

    pub startPosAfterHeader: UInt64,
    pub dataPos: UInt64,

    pub NumFiles: UInt32,

    pub UnpackPositions: *mut UInt64, // NumFiles + 1
    pub IsDirs: *mut Byte,
    pub CRCs: CSzBitUi32s,

    pub Attribs: CSzBitUi32s,
    pub MTime: CSzBitUi64s,
    pub CTime: CSzBitUi64s,

    pub FolderToFile: *mut UInt32, // NumFolders + 1
    pub FileToFolder: *mut UInt32, // NumFiles

    pub FileNameOffsets: *mut size_t,
    pub FileNames: *mut Byte, //UTF-16-LE
}

impl CSzArEx {
    ///Check is file by byte position 'i' is directory
    pub fn is_dir(&self, i: UInt32) -> bool {
        //#Define SzBitArray_Check(p, i) (((p)[(i) >> 3] & (0x80 >> ((i) & 7))) != 0)
        let is_dirs = unsafe { slice::from_raw_parts(self.IsDirs, self.NumFiles as usize) };

        (is_dirs[(i >> 3) as usize] & 0x80 >> (i & 7)) != 0
    }
}

impl Default for CSzArEx {
    fn default() -> Self {
        CSzArEx {
            db: Default::default(),
            startPosAfterHeader: 0,
            dataPos: 0,
            NumFiles: 0,
            UnpackPositions: ptr::null_mut(),
            IsDirs: ptr::null_mut(),
            CRCs: Default::default(),
            Attribs: Default::default(),
            MTime: Default::default(),
            CTime: Default::default(),
            FolderToFile: ptr::null_mut(),
            FileToFolder: ptr::null_mut(),
            FileNameOffsets: ptr::null_mut(),
            FileNames: ptr::null_mut(),
        }
    }
}

#[link(name = "lzma", kind = "static")]
extern "C" {
    pub fn InFile_Open(p: *mut CSzFile, name: *const c_char) -> WRes;

    pub fn FileInStream_CreateVTable(p: *mut CFileInStream);

    pub fn LookToRead2_CreateVTable(p: *mut CLookToRead2, lookahead: libc::c_int);

    pub fn CrcGenerateTable();

    pub fn SzAlloc(p: ISzAllocPtr, size: size_t) -> *mut c_void;

    pub fn SzFree(p: ISzAllocPtr, address: *mut c_void);

    pub fn SzArEx_Init(p: *mut CSzArEx);

    pub fn SzArEx_Open(
        p: *mut CSzArEx,
        in_stream: *mut ILookInStream,
        alloc_main: ISzAllocPtr,
        alloc_temp: ISzAllocPtr,
    ) -> SZ;

    pub fn SzArEx_GetFileNameUtf16(
        p: *const CSzArEx,
        file_index: size_t,
        dest: *mut UInt16,
    ) -> size_t;

    pub fn File_Close(p: *mut CSzFile) -> WRes;

    pub fn SzArEx_Free(p: *mut CSzArEx, alloc: ISzAllocPtr);

    pub fn SzArEx_Extract(
        p: *const CSzArEx,
        in_stream: *mut ILookInStream,
        file_index: UInt32,
        block_index: *mut UInt32,
        temp_buf: *mut *mut Byte,
        out_buffer_size: *mut size_t,
        offset: *mut size_t,
        out_size_processed: *mut size_t,
        alloc_main: ISzAllocPtr,
        alloc_temp: ISzAllocPtr,
    ) -> SZ;

}

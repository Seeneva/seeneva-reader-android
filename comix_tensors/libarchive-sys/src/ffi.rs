#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

use libc::{c_char, c_int, c_uint, c_ushort, c_void, mode_t, size_t, ssize_t};

pub type la_int64_t = i64;

#[cfg(windows)]
pub type __LA_MODE_T = c_ushort;
#[cfg(unix)]
pub type __LA_MODE_T = mode_t;

/*
 * Error codes: Use archive_errno() and archive_error_string()
 * to retrieve details.  Unless specified otherwise, all functions
 * that return 'int' use these codes.
 */
///Found end of archive.
pub const ARCHIVE_EOF: c_int = 1;
///Operation was successful.
pub const ARCHIVE_OK: c_int = 0;
///Retry might succeed.
pub const ARCHIVE_RETRY: c_int = -10;
///Partial success.
pub const ARCHIVE_WARN: c_int = -20;
///Current operation cannot complete.
pub const ARCHIVE_FAILED: c_int = -25;
///No more operations are possible.
pub const ARCHIVE_FATAL: c_int = -30;

//https://www.gnu.org/software/libc/manual/html_node/Testing-File-Type.html
/// Bit mask for file type (see man stat)
pub const AE_IFMT: c_uint = 0o170000;
/// Regular file
pub const AE_IFREG: c_uint = 0o100000;
/// Symbolic link
pub const AE_IFLNK: c_uint = 0o120000;
/// Socket
pub const AE_IFSOCK: c_uint = 0o140000;
/// Character device
pub const AE_IFCHR: c_uint = 0o020000;
/// Block device
pub const AE_IFBLK: c_uint = 0o060000;
/// Directory
pub const AE_IFDIR: c_uint = 0o040000;
/// Named pipe (fifo)
pub const AE_IFIFO: c_uint = 0o010000;

//https://doc.rust-lang.org/nomicon/ffi.html 'opaque' struct recommendation
#[repr(C)]
pub struct archive {
    _private: [u8; 0],
}
#[repr(C)]
pub struct archive_entry {
    _private: [u8; 0],
}

#[link(name = "archive", kind = "static")]
#[link(name = "lzma")]
#[link(name = "z")]
extern "C" {
    /*-
     * Basic outline for reading an archive:
     *   1) Ask archive_read_new for an archive reader object.
     *   2) Update any global properties as appropriate.
     *      In particular, you'll certainly want to call appropriate
     *      archive_read_support_XXX functions.
     *   3) Call archive_read_open_XXX to open the archive
     *   4) Repeatedly call archive_read_next_header to get information about
     *      successive archive entries.  Call archive_read_data to extract
     *      data for entries of interest.
     *   5) Call archive_read_free to end processing.
     */

    /// Allocates and initializes a struct archive object suitable for reading from an archive.
    /// NULL is returned on error.
    pub fn archive_read_new() -> *mut archive;

    /// Invokes archive_read_close() if it was not invoked manually, then release all resources
    pub fn archive_read_free(a: *mut archive) -> c_int;

    /// Enables support---including auto-detection code---for the specified archive format.
    /// For example, archive_read_support_format_tar() enables support for a variety of standard tar formats,
    /// old-style tar, ustar, pax interchange format, and many common variants.
    /// For convenience, archive_read_support_format_all() enables support for all available formats.
    /// Only empty archives are supported by default.
    pub fn archive_read_support_format_all(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_7zip(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_ar(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_cab(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_cpio(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_empty(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_gnutar(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_iso9660(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_lha(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_mtree(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_rar(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_rar5(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_raw(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_tar(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_warc(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_xar(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_zip(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_zip_streamable(archive: *mut archive) -> c_int;
    pub fn archive_read_support_format_zip_seekable(archive: *mut archive) -> c_int;

    /// Enables auto-detection code and decompression support for the
    /// specified compression.  These functions may fall back on external
    /// programs if an appropriate	library	was not	available at build
    /// time.  Decompression using	an external program is usually slower
    /// than decompression	through	built-in libraries.  Note that "none"
    /// is	always enabled by default.
    pub fn archive_read_support_filter_all(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_bzip2(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_compress(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_gzip(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_grzip(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_lrzip(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_lz4(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_lzip(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_lzma(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_lzop(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_none(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_rpm(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_uu(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_xz(arg: *mut archive) -> c_int;
    pub fn archive_read_support_filter_zstd(arg: *mut archive) -> c_int;
    // archive_read_support_filter_program
    // archive_read_support_filter_program_signature

    ///Read an archive that's already open, using the file descriptor
    pub fn archive_read_open_fd(archive: *mut archive, _fd: c_int, _block_size: size_t) -> c_int;

    /// Read the header for the next entry and return a pointer to a struct archive_entry.
    ///
    /// These functions return [ARCHIVE_OK] (the operation succeeded), [ARCHIVE_WARN] (the operation
    /// succeeded but a non-critical error was encountered), ARCHIVE_EOF (end-of-archive was
    /// encountered), [ARCHIVE_RETRY] (the operation failed but can be retried), and [ARCHIVE_FATAL]
    /// (there was a fatal error; the archive should be closed immediately).
    pub fn archive_read_next_header(archive: *mut archive, entry: *mut *mut archive_entry)
        -> c_int;

    pub fn archive_read_next_header2(archive: *mut archive, entry: *mut archive_entry) -> c_int;

    /// A convenience function that repeatedly calls archive_read_data_block() to skip all
    /// of the data for this archive entry.  Note that this function is invoked
    /// automatically by archive_read_next_header2() if the previous entry was not
    /// completely consumed.
    pub fn archive_read_data_skip(archive: *mut archive) -> c_int;

    /// Returns a count of the number of files processed by this archive object.
    /// The count is incremented by calls to [archive_write_header] or [archive_read_next_header].
    pub fn archive_file_count(archive: *mut archive) -> c_int;

    /// Return the next available block of data for this entry.
    /// Function avoids copying data and allows you to correctly handle sparse files, as supported by some archive formats.
    /// The library guarantees that offsets will increase and that blocks will not overlap.
    /// Note that the blocks returned from this function can be much larger than the block size read from disk,
    /// due to compression and internal buffer optimizations.
    pub fn archive_read_data_block(
        a: *mut archive,
        buff: *mut *const c_void,
        size: *mut size_t,
        offset: *mut la_int64_t,
    ) -> c_int;

    /// Read data associated with the header just read.
    /// Internally, this is a convenience function that calls archive_read_data_block() and
    /// fills any gaps with nulls so that callers see a single continuous stream of data.
    pub fn archive_read_data(archive: *mut archive, buff: *mut c_void, size: size_t) -> ssize_t;

    /// Returns a numeric error code indicating the reason for the most recent error return.
    pub fn archive_errno(archive: *mut archive) -> c_int;

    /// Returns a textual error message suitable for display.
    /// The error message here is usually more specific than that obtained from passing the result of archive_errno to strerror.
    pub fn archive_error_string(archive: *mut archive) -> *const c_char;

    /// Detailed textual name/version of the library and its dependencies.
    /// This has the form:
    ///    "libarchive x.y.z zlib/a.b.c liblzma/d.e.f ... etc ..."
    /// the list of libraries described here will vary depending on how
    /// libarchive was compiled.
    pub fn archive_version_details() -> *const c_char;

    // #################### ENTRY #############################3
    // archive_entry.h

    /// Allocate and return a blank struct archive_entry object
    pub fn archive_entry_new() -> *mut archive_entry;

    pub fn archive_entry_new2(a: *mut archive) -> *mut archive_entry;

    /// Erases the object, resetting all internal fields to the same state as a newly-created object.
    /// This is provided to allow you to quickly recycle objects without thrashing the heap.
    pub fn archive_entry_clear(a: *mut archive_entry) -> *mut archive_entry;

    /// A deep copy operation; all text fields are duplicated.
    pub fn archive_entry_clone(a: *mut archive_entry) -> *mut archive_entry;

    /// Releases the struct archive_entry object.
    pub fn archive_entry_free(a: *mut archive_entry);

    pub fn archive_entry_pathname_utf8(entry: *mut archive_entry) -> *const c_char;

    /// File type
    pub fn archive_entry_filetype(entry: *mut archive_entry) -> __LA_MODE_T;

    /// File size
    pub fn archive_entry_size(entry: *mut archive_entry) -> la_int64_t;
}

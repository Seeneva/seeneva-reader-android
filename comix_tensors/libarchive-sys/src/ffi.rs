#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

use libc::*;

// libc file types for archive_entry_filetype
//https://www.gnu.org/software/libc/manual/html_node/Testing-File-Type.html
pub use libc::{
    S_IFBLK as AE_IFBLK, S_IFCHR as AE_IFCHR, S_IFDIR as AE_IFDIR, S_IFIFO as AE_IFIFO,
    S_IFLNK as AE_IFLNK, S_IFMT as AE_IFMT, S_IFREG as AE_IFREG, S_IFSOCK as AE_IFSOCK,
};

pub use libc::{mode_t, time_t, FILE};

include!(concat!(env!("OUT_DIR"), "/bindings.rs"));

use jni::sys::{jobject, JNIEnv};
use std::os::raw::{c_char, c_int, c_void};

pub type AAssetManager = c_void;
pub type AAsset = c_void;

//#[repr(C)]
//#[derive(Debug, Default)]
//pub struct AndroidBitmapInfo {
//    pub width: c_uint,
//    pub height: c_uint,
//    pub stride: c_uint,
//    pub format: c_int,
//    pub flags: c_uint,
//}
//
//impl AndroidBitmapInfo {
//    pub fn image_size(&self) -> c_uint {
//        self.stride * self.height
//    }
//}

//pub const AASSET_MODE_UNKNOWN: c_int = 0;
//pub const AASSET_MODE_RANDOM: c_int = 1;
//pub const AASSET_MODE_STREAMING: c_int = 2;
pub const AASSET_MODE_BUFFER: c_int = 3;

//#[link(name = "jnigraphics")]
//extern "C" {
//    pub fn AndroidBitmap_getInfo(
//        env: *mut sys::JNIEnv,
//        jbitmap: sys::jobject,
//        info: &AndroidBitmapInfo,
//    ) -> c_int;
//
//    pub fn AndroidBitmap_lockPixels(
//        env: *mut sys::JNIEnv,
//        jbitmap: sys::jobject,
//        addrPtr: *mut *mut c_void,
//    ) -> c_int;
//
//    pub fn AndroidBitmap_unlockPixels(env: *mut sys::JNIEnv, jbitmap: sys::jobject) -> c_int;
//}

#[link(name = "android")]
extern "C" {
    pub fn AAssetManager_fromJava(env: *mut JNIEnv, assetManager: jobject) -> *mut AAssetManager;

    pub fn AAssetManager_open(
        mgr: *mut AAssetManager,
        filename: *const c_char,
        mode: c_int,
    ) -> *mut AAsset;

    pub fn AAsset_close(asset: *mut AAsset);

    pub fn AAsset_getBuffer(asset: *mut AAsset) -> *const c_void;

    pub fn AAsset_getLength(asset: *mut AAsset) -> usize;
}

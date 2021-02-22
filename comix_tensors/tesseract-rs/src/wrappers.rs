use std::ffi::CStr;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::marker::PhantomData;
use std::ops::Deref;
use std::os::raw::{c_char, c_int};

use tesseract_sys;

/// Helper to delete C pointers
pub trait DeletePointer {
    /// Delete C pointer
    unsafe fn delete(self);
}

// C string
impl DeletePointer for *mut c_char {
    unsafe fn delete(self) {
        tesseract_sys::TessDeleteText(self);
    }
}

// C int array
impl DeletePointer for *mut c_int {
    unsafe fn delete(self) {
        tesseract_sys::TessDeleteIntArray(self);
    }
}

// C string array
impl DeletePointer for *mut *mut c_char {
    unsafe fn delete(self) {
        tesseract_sys::TessDeleteTextArray(self);
    }
}

/// Tesseract result text
#[derive(Debug)]
pub struct Text(*mut c_char);

impl Text {
    ///Wrap pointer
    #[track_caller]
    fn new(ptr: *mut c_char) -> Self {
        if ptr.is_null() {
            panic!("Text pointer cannot be null");
        }

        Text(ptr)
    }
}

impl Display for Text {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        Display::fmt(self.deref(), f)
    }
}

impl Deref for Text {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        unsafe { CStr::from_ptr(self.0) }
            .to_str()
            .expect("Should be UTF8 string")
    }
}

impl From<*mut c_char> for Text {
    fn from(ptr: *mut c_char) -> Self {
        Self::new(ptr)
    }
}

impl Drop for Text {
    fn drop(&mut self) {
        unsafe {
            tesseract_sys::TessDeleteText(self.0);
        }
    }
}

/// Represent Tesseract arrays
/// `T` - input C type
/// `O` - slice output type
#[derive(Debug)]
pub struct Array<T, O>
where
    *mut T: DeletePointer,
{
    ptr: *mut T,
    pd: PhantomData<O>,
}

impl<T, O> Array<T, O>
where
    *mut T: DeletePointer,
{
    /// Wrap array pointer
    #[track_caller]
    fn new(ptr: *mut T) -> Self {
        if ptr.is_null() {
            panic!("Array pointer cannot be null");
        }

        Array {
            ptr,
            pd: PhantomData,
        }
    }
}

impl<O> Array<c_int, O> {
    ///Return array as Rust slice
    pub fn as_slice(&self) -> &[O] {
        unsafe {
            let mut ptr = self.ptr;

            let mut array_size = 0usize;

            while let Some(val) = ptr.as_ref() {
                if val == &-1 {
                    break;
                }

                array_size += 1;

                ptr = ptr.add(1);
            }

            std::slice::from_raw_parts(self.ptr.cast::<O>(), array_size)
        }
    }
}

impl<T, O> From<*mut T> for Array<T, O>
where
    *mut T: DeletePointer,
{
    fn from(ptr: *mut T) -> Self {
        Self::new(ptr)
    }
}

impl<T, O> Drop for Array<T, O>
where
    *mut T: DeletePointer,
{
    fn drop(&mut self) {
        unsafe {
            self.ptr.delete();
        }
    }
}

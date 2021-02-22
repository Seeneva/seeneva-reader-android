use std::fmt::{Debug, Formatter, Result as FmtResult};
use std::marker::PhantomData;

///Default debug formatter for TennsorFlow Raw pointers
fn debug_fmt_pointer<T, P: TfLitePointer<T>>(ptr: &P, f: &mut Formatter) -> FmtResult {
    f.debug_struct("TfLitePointer")
        .field("ptr", &format_args!("{:p}", ptr.as_ptr()))
        .finish()
}

///Base trait for TensorFlow raw pointer wrappers
pub(super) trait TfLitePointer<T>: Debug {
    ///Create new wrapper aroud provided raw pointer
    fn new(ptr: *mut T) -> Self;
    ///Return inner TensorFlow pointer
    fn as_ptr(&self) -> *mut T;
}

///Wrapper for not owned TF Lite raw pointers. They shouldn't be dropped.
pub(super) struct BorrowedTfLitePointer<'a, T> {
    ///Inner TensorFlow Lite pointer
    ptr: *mut T,
    pd: PhantomData<&'a ()>,
}

impl<T> Debug for BorrowedTfLitePointer<'_, T> {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        debug_fmt_pointer(self, f)
    }
}

impl<T> From<*mut T> for BorrowedTfLitePointer<'_, T> {
    fn from(ptr: *mut T) -> Self {
        Self::new(ptr)
    }
}

impl<T> TfLitePointer<T> for BorrowedTfLitePointer<'_, T> {
    fn new(ptr: *mut T) -> Self {
        if ptr.is_null() {
            panic!("Can't init Tensor Flow Lite BorrowedTfLitePointer. Raw pointer is null");
        }

        BorrowedTfLitePointer {
            ptr,
            pd: PhantomData,
        }
    }

    fn as_ptr(&self) -> *mut T {
        self.ptr
    }
}

//=================================CLEAR==========================================================

///Trait to indicate which raw pointers can be deleted using TF Lite c_api
pub(super) trait DeleteTfLitePointer {
    ///Clear TF lite pointer and its resources
    fn delete(&mut self);
}

impl DeleteTfLitePointer for *mut tflite_sys::TfLiteModel {
    fn delete(&mut self) {
        unsafe {
            tflite_sys::TfLiteModelDelete(*self);
        }
    }
}

impl DeleteTfLitePointer for *mut tflite_sys::TfLiteInterpreterOptions {
    fn delete(&mut self) {
        unsafe {
            tflite_sys::TfLiteInterpreterOptionsDelete(*self);
        }
    }
}

impl DeleteTfLitePointer for *mut tflite_sys::TfLiteInterpreter {
    fn delete(&mut self) {
        unsafe {
            tflite_sys::TfLiteInterpreterDelete(*self);
        }
    }
}

impl DeleteTfLitePointer for *mut tflite_sys::TfLiteTensor {
    fn delete(&mut self) {
        unsafe {
            tflite_sys::TfLiteTensorFree(*self);
        }
    }
}

impl DeleteTfLitePointer for *mut tflite_sys::TfLiteDelegate {
    fn delete(&mut self) {
        unsafe {
            tflite_sys::TfLiteGpuDelegateV2Delete(*self);
        }
    }
}

//================================================================================================

///Wrapper for owned TF Lite raw pointers. They will be cleared.
pub(super) struct OwnedTfLitePointer<T>(*mut T)
where
    *mut T: DeleteTfLitePointer;

impl<T> Debug for OwnedTfLitePointer<T>
where
    *mut T: DeleteTfLitePointer,
{
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        debug_fmt_pointer(self, f)
    }
}

impl<T> From<*mut T> for OwnedTfLitePointer<T>
where
    *mut T: DeleteTfLitePointer,
{
    fn from(ptr: *mut T) -> Self {
        Self::new(ptr)
    }
}

impl<T> TfLitePointer<T> for OwnedTfLitePointer<T>
where
    *mut T: DeleteTfLitePointer,
{
    fn new(ptr: *mut T) -> Self {
        if ptr.is_null() {
            panic!("Can't init Tensor Flow Lite OwnedTfLitePointer. Raw pointer is null");
        }

        OwnedTfLitePointer(ptr)
    }

    fn as_ptr(&self) -> *mut T {
        self.0
    }
}

impl<T> Drop for OwnedTfLitePointer<T>
where
    *mut T: DeleteTfLitePointer,
{
    fn drop(&mut self) {
        if !self.0.is_null() {
            self.0.delete()
        }
    }
}

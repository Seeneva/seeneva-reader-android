use std::convert::TryInto;

use super::{AsLeptonicaPtr, Pix, Result};

pub trait GrayMorph: AsLeptonicaPtr {
    fn erode_gray(&self, h_size: i32, v_size: i32) -> Result<Pix> {
        unsafe { leptonica_sys::pixErodeGray(self.as_ptr(), h_size as _, v_size as _) }.try_into()
    }

    fn dilate_gray(&self, h_size: i32, v_size: i32) -> Result<Pix> {
        unsafe { leptonica_sys::pixDilateGray(self.as_ptr(), h_size as _, v_size as _) }.try_into()
    }
}

impl<T: AsLeptonicaPtr> GrayMorph for T {}

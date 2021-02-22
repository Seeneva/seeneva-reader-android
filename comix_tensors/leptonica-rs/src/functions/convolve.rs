use std::convert::TryInto;

use super::{AsLeptonicaPtr, Pix, Result};

pub trait Convolve: AsLeptonicaPtr {
    ///\param[in]    pix      8 or 32 bpp; or 2, 4 or 8 bpp with colormap
    ///\param[in]    wc, hc   half width/height of convolution kernel
    ///\return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) The full width and height of the convolution kernel
    ///         are (2 * wc + 1) and (2 * hc + 1)
    ///     (2) Returns a copy if either wc or hc are 0
    ///     (3) Require that w >= 2 * wc + 1 and h >= 2 * hc + 1,
    ///         where (w,h) are the dimensions of pixs.  Attempt to
    ///         reduce the kernel size if necessary.
    fn block_conv(&self, wc: i32, hc: i32) -> Result<Pix> {
        unsafe { leptonica_sys::pixBlockconv(self.as_ptr(), wc as _, hc as _) }.try_into()
    }

    fn block_conv_accum(&self) -> Result<Pix>{
        unsafe { leptonica_sys::pixBlockconvAccum(self.as_ptr()) }.try_into()
    }

    ///param[in]    pixs        8 or 32 bpp grayscale
    ///param[in]    wc, hc      half width/height of convolution kernel
    ///param[in]    hasborder   use 1 if it already has (wc + 1 border pixels
    ///                          on left and right, and hc + 1 on top and bottom;
    ///                          use 0 to add kernel-dependent border)
    ///param[in]    normflag    1 for normalization to get average in window;
    ///                          0 for the sum in the window (un-normalized)
    ///return  pixd 8 or 32 bpp, average over kernel window
    ///
    ///Notes:
    ///     (1) The input and output depths are the same.
    ///     (2) A set of border pixels of width (wc + 1) on left and right,
    ///         and of height (hc + 1) on top and bottom, must be on the
    ///         pix before the accumulator is found.  The output pixd
    ///         (after convolution) has this border removed.
    ///         If %hasborder = 0, the required border is added.
    ///     (3) Typically, %normflag == 1.  However, if you want the sum
    ///         within the window, rather than a normalized convolution,
    ///         use %normflag == 0.
    ///     (4) This builds a block accumulator pix, uses it here, and
    ///         destroys it.
    ///     (5) The added border, along with the use of an accumulator array,
    ///         allows computation without special treatment of pixels near
    ///         the image boundary, and runs in a time that is independent
    ///         of the size of the convolution kernel.
    fn windowed_mean(&self, wc: i32, hc: i32, has_border: bool, norm_flag: bool) -> Result<Pix> {
        unsafe {
            leptonica_sys::pixWindowedMean(
                self.as_ptr(),
                wc as _,
                hc as _,
                has_border as _,
                norm_flag as _,
            )
        }
        .try_into()
    }

    ///\param[in]    pixs        8 bpp grayscale
    ///\param[in]    wc, hc      half width/height of convolution kernel
    ///\param[in]    hasborder   use 1 if it already has (wc + 1 border pixels
    ///                          on left and right, and hc + 1 on top and bottom;
    ///                          use 0 to add kernel-dependent border)
    ///\return  pixd    32 bpp, average over rectangular window of
    ///                 width = 2 * wc + 1 and height = 2 * hc + 1
    ///
    ///Notes:
    ///     (1) A set of border pixels of width (wc + 1) on left and right,
    ///         and of height (hc + 1) on top and bottom, must be on the
    ///         pix before the accumulator is found.  The output pixd
    ///         (after convolution) has this border removed.
    ///         If %hasborder = 0, the required border is added.
    ///     (2) The advantage is that we are unaffected by the boundary, and
    ///         it is not necessary to treat pixels within %wc and %hc of the
    ///         border differently.  This is because processing for pixd
    ///         only takes place for pixels in pixs for which the
    ///         kernel is entirely contained in pixs.
    ///     (3) Why do we have an added border of width (%wc + 1) and
    ///         height (%hc + 1), when we only need %wc and %hc pixels
    ///         to satisfy this condition?  Answer: the accumulators
    ///         are asymmetric, requiring an extra row and column of
    ///         pixels at top and left to work accurately.
    ///     (4) The added border, along with the use of an accumulator array,
    ///         allows computation without special treatment of pixels near
    ///         the image boundary, and runs in a time that is independent
    ///         of the size of the convolution kernel.
    fn windowed_mean_square(&self, wc: i32, hc: i32, has_border: bool) -> Result<Pix> {
        unsafe {
            leptonica_sys::pixWindowedMeanSquare(self.as_ptr(), wc as _, hc as _, has_border as _)
        }
        .try_into()
    }

    ///\param[in]    pixs     8 bpp gray or 32 bpp rgb; no colormap
    ///\param[in]    stdev    of noise
    ///\return  pixd    8 or 32 bpp, or NULL on error
    ///
    ///Notes:
    ///     (1) This adds noise to each pixel, taken from a normal
    ///         distribution with zero mean and specified standard deviation.
    fn add_gaussian_noise(&self, stdev: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixAddGaussianNoise(self.as_ptr(), stdev as _) }.try_into()
    }
}

impl<T: AsLeptonicaPtr> Convolve for T {}

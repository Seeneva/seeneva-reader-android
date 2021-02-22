use std::convert::TryInto;

use super::{AsLeptonicaPtr, Pix, Result};

pub trait Bilateral: AsLeptonicaPtr {
    //*********************************************************************************************
    // ========== Top level approximate separable grayscale or color bilateral filtering ==========
    //*********************************************************************************************

    ///\param[in]    pixs            8 bpp gray or 32 bpp rgb, no colormap
    ///\param[in]    spatial_stdev   of gaussian kernel; in pixels, > 0.5
    ///\param[in]    range_stdev     of gaussian range kernel; > 5.0; typ. 50.0
    ///\param[in]    ncomps          number of intermediate sums J(k,x);
    ///                              in [4 ... 30]
    ///\param[in]    reduction       1, 2 or 4
    ///\return  pixd   bilateral filtered image, or NULL on error
    ///
    ///Notes:
    ///     (1) This performs a relatively fast, separable bilateral
    ///         filtering operation.  The time is proportional to ncomps
    ///         and varies inversely approximately as the cube of the
    ///         reduction factor.  See bilateral.h for algorithm details.
    ///     (2) We impose minimum values for range_stdev and ncomps to
    ///         avoid nasty artifacts when either are too small.  We also
    ///         impose a constraint on their product:
    ///              ncomps * range_stdev >= 100.
    ///         So for values of range_stdev >= 25, ncomps can be as small as 4.
    ///         Here is a qualitative, intuitive explanation for this constraint.
    ///         Call the difference in k values between the J(k) == 'delta', where
    ///             'delta' ~ 200 / ncomps
    ///         Then this constraint is roughly equivalent to the condition:
    ///             'delta' < 2 * range_stdev
    ///         Note that at an intensity difference of (2 * range_stdev), the
    ///         range part of the kernel reduces the effect by the factor 0.14.
    ///         This constraint requires that we have a sufficient number of
    ///         PCBs (i.e, a small enough 'delta'), so that for any value of
    ///         image intensity I, there exists a k (and a PCB, J(k), such that
    ///             |I - k| < range_stdev
    ///         Any fewer PCBs and we don't have enough to support this condition.
    ///     (3) The upper limit of 30 on ncomps is imposed because the
    ///         gain in accuracy is not worth the extra computation.
    ///     (4) The size of the gaussian kernel is twice the spatial_stdev
    ///         on each side of the origin.  The minimum value of
    ///         spatial_stdev, 0.5, is required to have a finite sized
    ///         spatial kernel.  In practice, a much larger value is used.
    ///     (5) Computation of the intermediate images goes inversely
    ///         as the cube of the reduction factor.  If you can use a
    ///         reduction of 2 or 4, it is well-advised.
    ///     (6) The range kernel is defined over the absolute value of pixel
    ///         grayscale differences, and hence must have size 256 x 1.
    ///         Values in the array represent the multiplying weight
    ///         depending on the absolute gray value difference between
    ///         the source pixel and the neighboring pixel, and should
    ///         be monotonically decreasing.
    ///     (7) Interesting observation.  Run this on prog/fish24.jpg, with
    ///         range_stdev = 60, ncomps = 6, and spatial_dev = {10, 30, 50}.
    ///         As spatial_dev gets larger, we get the counter-intuitive
    ///         result that the body of the red fish becomes less blurry.
    fn bilateral(
        &self,
        spatial_stdev: f32,
        range_stdev: f32,
        ncomps: i32,
        reduction: i32,
    ) -> Result<Pix> {
        unsafe {
            leptonica_sys::pixBilateral(
                self.as_ptr(),
                spatial_stdev,
                range_stdev,
                ncomps,
                reduction,
            )
        }
        .try_into()
    }
}

impl<T: AsLeptonicaPtr> Bilateral for T {}

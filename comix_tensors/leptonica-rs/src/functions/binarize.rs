use std::convert::TryInto;

use crate::error::{Error, SysErrorKind};

use super::{AsLeptonicaPtr, Pix, Result};

pub trait Binarize: AsLeptonicaPtr {
    ///param[in]    pixs              8 bpp
    ///param[in]    sx, sy            desired tile dimensions; actual size may vary
    ///param[in]    smoothx, smoothy  half-width of convolution kernel applied to
    ///                                threshold array: use 0 for no smoothing
    ///param[in]    scorefract        fraction of the max Otsu score; typ. 0.1;
    ///                                use 0.0 for standard Otsu
    ///param[out]   ppixth            [optional] array of threshold values
    ///                                found for each tile
    ///param[out]   ppixd             [optional] thresholded input pixs,
    ///                                based on the threshold array
    ///return  0 if OK, 1 on error
    ///
    ///Notes:
    ///     (1) The Otsu method finds a single global threshold for an image.
    ///         This function allows a locally adapted threshold to be
    ///         found for each tile into which the image is broken up.
    ///     (2) The array of threshold values, one for each tile, constitutes
    ///         a highly downscaled image.  This array is optionally
    ///         smoothed using a convolution.  The full width and height of the
    ///         convolution kernel are (2 * %smoothx + 1) and (2 * %smoothy + 1).
    ///     (3) The minimum tile dimension allowed is 16.  If such small
    ///         tiles are used, it is recommended to use smoothing, because
    ///         without smoothing, each small tile determines the splitting
    ///         threshold independently.  A tile that is entirely in the
    ///         image bg will then hallucinate fg, resulting in a very noisy
    ///         binarization.  The smoothing should be large enough that no
    ///         tile is only influenced by one type (fg or bg) of pixels,
    ///         because it will force a split of its pixels.
    ///     (4) To get a single global threshold for the entire image, use
    ///         input values of %sx and %sy that are larger than the image.
    ///         For this situation, the smoothing parameters are ignored.
    ///     (5) The threshold values partition the image pixels into two classes:
    ///         one whose values are less than the threshold and another
    ///         whose values are greater than or equal to the threshold.
    ///         This is the same use of 'threshold' as in pixThresholdToBinary().
    ///     (6) The scorefract is the fraction of the maximum Otsu score, which
    ///         is used to determine the range over which the histogram minimum
    ///         is searched.  See numaSplitDistribution() for details on the
    ///         underlying method of choosing a threshold.
    ///     (7) This uses enables a modified version of the Otsu criterion for
    ///         splitting the distribution of pixels in each tile into a
    ///         fg and bg part.  The modification consists of searching for
    ///         a minimum in the histogram over a range of pixel values where
    ///         the Otsu score is within a defined fraction, %scorefract,
    ///         of the max score.  To get the original Otsu algorithm, set
    ///         %scorefract == 0.
    ///     (8) N.B. This method is NOT recommended for images with weak text
    ///         and significant background noise, such as bleedthrough, because
    ///         of the problem noted in (3) above for tiling.  Use Sauvola.
    fn otsu_adaptive_threshold(
        &self,
        sx: i32,
        sy: i32,
        smoothx: i32,
        smoothy: i32,
        scorefract: f32,
    ) -> Result<Pix> {
        let mut ppixd = std::ptr::null_mut::<leptonica_sys::Pix>();

        let result = unsafe {
            leptonica_sys::pixOtsuAdaptiveThreshold(
                self.as_ptr(),
                sx as _,
                sy as _,
                smoothx as _,
                smoothy as _,
                scorefract,
                std::ptr::null_mut(),
                &mut ppixd as _,
            )
        };

        match result {
            0 => Ok(ppixd),
            1 => Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Option::<&str>::None,
            )),
            _ => unreachable!(),
        }
        .and_then(TryInto::try_into)
    }

    fn otsu_thresh_on_background_norm(
        &self,
        sx: i32,
        sy: i32,
        thresh: i32,
        min_count: i32,
        bg_val: i32,
        smooth_x: i32,
        smooth_y: i32,
        score_fract: f32,
    ) -> Result<Pix> {
        unsafe {
            leptonica_sys::pixOtsuThreshOnBackgroundNorm(
                self.as_ptr(),
                std::ptr::null_mut(),
                sx as _,
                sy as _,
                thresh as _,
                min_count as _,
                bg_val as _,
                smooth_x as _,
                smooth_y as _,
                score_fract as _,
                std::ptr::null_mut(),
            )
        }
        .try_into()
    }

    fn sauvola_binarize_tiled(&self, wh_size: i32, factor: f32, nx: i32, ny: i32) -> Result<Pix> {
        let mut ppixd = std::ptr::null_mut::<leptonica_sys::Pix>();

        let result = unsafe {
            leptonica_sys::pixSauvolaBinarizeTiled(
                self.as_ptr(),
                wh_size,
                factor,
                nx,
                ny,
                std::ptr::null_mut(),
                &mut ppixd as _,
            )
        };

        match result {
            0 => Ok(ppixd),
            1 => Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Option::<&str>::None,
            )),
            _ => unreachable!(),
        }
        .and_then(TryInto::try_into)
    }

    fn sauvola_binarize(&self, whsize: i32, factor: f32, add_border: i32) -> Result<Pix> {
        let mut ppixd = std::ptr::null_mut::<leptonica_sys::Pix>();

        let result = unsafe {
            leptonica_sys::pixSauvolaBinarize(
                self.as_ptr(),
                whsize,
                factor,
                add_border,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                &mut ppixd as _,
            )
        };

        match result {
            0 => Ok(ppixd),
            1 => Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Option::<&str>::None,
            )),
            _ => unreachable!(),
        }
        .and_then(TryInto::try_into)
    }
}

impl<T: AsLeptonicaPtr> Binarize for T {}

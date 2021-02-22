use super::{AsLeptonicaPtr, Pix, Result};

use std::convert::TryInto;

pub trait AdaptMap: AsLeptonicaPtr {
    //*********************************************************************************************
    // ================== Clean background to white using background normalization =================
    //*********************************************************************************************

    ///\param[in]    pixs       8 bpp grayscale or 32 bpp rgb
    ///\param[in]    pixim      [optional] 1 bpp 'image' mask; can be null
    ///\param[in]    pixg       [optional] 8 bpp grayscale version; can be null
    ///\param[in]    gamma      gamma correction; must be > 0.0; typically ~1.0
    ///\param[in]    blackval   dark value to set to black (0)
    ///\param[in]    whiteval   light value to set to white (255)
    ///\return  pixd 8 bpp or 32 bpp rgb, or NULL on error
    ///
    ///Notes:
    ///   (1) This is a simplified interface for cleaning an image.
    ///       For comparison, see pixAdaptThresholdToBinaryGen().
    ///   (2) The suggested default values for the input parameters are:
    ///         gamma:    1.0  (reduce this to increase the contrast; e.g.,
    ///                         for light text)
    ///         blackval   70  (a bit more than 60)
    ///         whiteval  190  (a bit less than 200)
    fn clean_background_to_white(
        &self,
        mask: Option<&impl AsLeptonicaPtr>,
        grayscale: Option<&impl AsLeptonicaPtr>,
        gamma: f32,
        black_val: i32,
        white_val: i32,
    ) -> Result<Pix> {
        let mask_ptr = match mask {
            Some(mask) => mask.as_ptr(),
            _ => std::ptr::null_mut(),
        };

        let grayscale_ptr = match grayscale {
            Some(grayscale) => grayscale.as_ptr(),
            _ => std::ptr::null_mut(),
        };

        unsafe {
            leptonica_sys::pixCleanBackgroundToWhite(
                self.as_ptr(),
                mask_ptr,
                grayscale_ptr,
                gamma,
                black_val,
                white_val,
            )
        }
        .try_into()
    }

    //*********************************************************************************************
    // ============================ Adaptive contrast normalization ===============================
    //*********************************************************************************************

    fn contrast_norm_into_copy(
        &self,
        sx: i32,
        sy: i32,
        min_diff: i32,
        smooth_x: i32,
        smooth_y: i32,
    ) -> Result<Pix> {
        contrast_norm_inner(
            self.as_ptr(),
            std::ptr::null_mut(),
            sx,
            sy,
            min_diff,
            smooth_x,
            smooth_y,
        )
    }

    fn contrast_norm(
        &mut self,
        sx: i32,
        sy: i32,
        min_diff: i32,
        smooth_x: i32,
        smooth_y: i32,
    ) -> Result<()> {
        contrast_norm_inner(
            self.as_ptr(),
            self.as_ptr(),
            sx,
            sy,
            min_diff,
            smooth_x,
            smooth_y,
        )
        .map(|pix| {
            std::mem::forget(pix);

            ()
        })
    }
}

impl<T: AsLeptonicaPtr> AdaptMap for T {}

///\param[in]    pixd               [optional] 8 bpp; null or equal to pixs
///\param[in]    pixs               8 bpp grayscale; not colormapped
///\param[in]    sx, sy             tile dimensions
///\param[in]    mindiff            minimum difference to accept as valid
///\param[in]    smoothx, smoothy   half-width of convolution kernel applied to
///                                 min and max arrays: use 0 for no smoothing
///\return  pixd always
///
///Notes:
///     (1) This function adaptively attempts to expand the contrast
///         to the full dynamic range in each tile.  If the contrast in
///         a tile is smaller than %mindiff, it uses the min and max
///         pixel values from neighboring tiles.  It also can use
///         convolution to smooth the min and max values from
///         neighboring tiles.  After all that processing, it is
///         possible that the actual pixel values in the tile are outside
///         the computed [min ... max] range for local contrast
///         normalization.  Such pixels are taken to be at either 0
///         (if below the min) or 255 (if above the max).
///     (2) pixd can be equal to pixs (in-place operation) or
///         null (makes a new pixd).
///     (3) sx and sy give the tile size; they are typically at least 20.
///     (4) mindiff is used to eliminate results for tiles where it is
///         likely that either fg or bg is missing.  A value around 50
///         or more is reasonable.
///     (5) The full width and height of the convolution kernel
///         are (2 * smoothx + 1) and (2 * smoothy + 1).  Some smoothing
///         is typically useful, and we limit the smoothing half-widths
///         to the range from 0 to 8.
///     (6) A linear TRC (gamma = 1.0) is applied to increase the contrast
///         in each tile.  The result can subsequently be globally corrected,
///         by applying pixGammaTRC() with arbitrary values of gamma
///         and the 0 and 255 points of the mapping.
fn contrast_norm_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    sx: i32,
    sy: i32,
    min_diff: i32,
    smooth_x: i32,
    smooth_y: i32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixContrastNorm(dst, src, sx, sy, min_diff, smooth_x, smooth_y) }
        .try_into()
}

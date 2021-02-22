use super::{AsLeptonicaPtr, Pix, Result};

use std::convert::TryInto;

pub trait Enhance: AsLeptonicaPtr {
    //*********************************************************************************************
    // ==================== Gamma TRC (tone reproduction curve) mapping ============================
    //*********************************************************************************************

    /// Copy source image and set gamma
    fn gamma_trc_into_copy(&mut self, gamma: f32, min: i32, max: i32) -> Result<Pix> {
        gamma_trc_inner(self.as_ptr(), std::ptr::null_mut(), gamma, min, max)
    }

    /// Change gamma in place
    fn gamma_trc(&mut self, gamma: f32, min: i32, max: i32) -> Result<()> {
        gamma_trc_inner(self.as_ptr(), self.as_ptr(), gamma, min, max).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    //*********************************************************************************************
    // ================================= Contrast enhancement ======================================
    //*********************************************************************************************

    /// Make a copy and set contrast
    fn contrast_trc_into_copy(&self, factor: f32) -> Result<Pix> {
        // copy this PIX and set contrast value
        contrast_trc_inner(self.as_ptr(), std::ptr::null_mut(), factor)
    }

    /// Set contrast in place
    fn contrast_trc(&mut self, factor: f32) -> Result<()> {
        // set contrast value in place so we do not to wrap pointer to the Rust struct
        contrast_trc_inner(self.as_ptr(), self.as_ptr(), factor).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    /// Create a copy and set contrast
    fn contrast_trc_masked_into(
        &self,
        mask: Option<&impl AsLeptonicaPtr>,
        factor: f32,
    ) -> Result<Pix> {
        let mask = match mask {
            Some(mask) => mask.as_ptr(),
            _ => std::ptr::null_mut(),
        };

        contrast_trc_masked_inner(self.as_ptr(), std::ptr::null_mut(), mask, factor)
    }

    /// Set contrast in place
    fn contrast_trc_masked(
        &mut self,
        mask: Option<&impl AsLeptonicaPtr>,
        factor: f32,
    ) -> Result<()> {
        let mask = match mask {
            Some(mask) => mask.as_ptr(),
            _ => std::ptr::null_mut(),
        };

        contrast_trc_masked_inner(self.as_ptr(), self.as_ptr(), mask, factor).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    //*********************************************************************************************
    // ================================ Histogram equalization =====================================
    //*********************************************************************************************

    fn equalize_trc_into_copy(&self, fract: f32, factor: i32) -> Result<Pix> {
        equalize_trc_inner(self.as_ptr(), std::ptr::null_mut(), fract, factor)
    }

    /// Set in place
    fn equalize_trc(&mut self, fract: f32, factor: i32) -> Result<()> {
        equalize_trc_inner(self.as_ptr(), self.as_ptr(), fract, factor).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    //*********************************************************************************************
    // =========================== Hue and saturation modification =================================
    //*********************************************************************************************

    /// Make a copy of the PIX and modify HUE
    fn modify_hue_into_copy(&self, fract: f32) -> Result<Pix> {
        modify_hue_inner(self.as_ptr(), std::ptr::null_mut(), fract)
    }

    /// Modify HUE in place
    fn modify_hue(&mut self, fract: f32) -> Result<()> {
        modify_hue_inner(self.as_ptr(), self.as_ptr(), fract).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    /// Copy source to the destination and set saturation
    fn modify_saturation_into(&self, fract: f32, dst: &mut impl AsLeptonicaPtr) -> Result<()> {
        modify_saturation_inner(self.as_ptr(), dst.as_ptr(), fract).map(|pix| {
            //We do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    /// Create a copy of this PIX and set saturation for the result image
    fn modify_saturation_into_copy(&self, fract: f32) -> Result<Pix> {
        modify_saturation_inner(self.as_ptr(), std::ptr::null_mut(), fract)
    }

    /// set in place
    fn modify_saturation(&mut self, fract: f32) -> Result<()> {
        modify_saturation_inner(self.as_ptr(), self.as_ptr(), fract).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    /// Make a copy of the image and set brightness
    fn modify_brightness_into_copy(&self, fract: f32) -> Result<Pix> {
        modify_brightness_inner(self.as_ptr(), std::ptr::null_mut(), fract)
    }

    /// Modify in place
    fn modify_brightness(&mut self, fract: f32) -> Result<()> {
        modify_brightness_inner(self.as_ptr(), self.as_ptr(), fract).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    //*********************************************************************************************
    // =========================== Darken gray (unsaturated) pixels ================================
    //*********************************************************************************************

    /// Create a copy of the image and apply function
    fn darken_gray_into_copy(&self, thresh: i32, sat_limit: i32) -> Result<Pix> {
        darken_gray_inner(self.as_ptr(), std::ptr::null_mut(), thresh, sat_limit)
    }

    /// Set in place
    fn darken_gray(&mut self, thresh: i32, sat_limit: i32) -> Result<()> {
        darken_gray_inner(self.as_ptr(), self.as_ptr(), thresh, sat_limit).map(|pix| {
            //We set in place so we do not need to have result PIX
            std::mem::forget(pix);

            ()
        })
    }

    //*********************************************************************************************
    // ====================== General multiplicative constant color transform ======================
    //*********************************************************************************************

    ///     Input:  pixs (colormapped or rgb)
    ///             [`r_fact`], [`g_fact`], [`b_fact`] (multiplicative factors on each component)
    ///     Return: pixd (colormapped or rgb, with colors scaled), or null on error
    ///
    /// Notes:
    ///     (1) [`r_fact`], [`g_fact`] and [`b_fact`] can only have non-negative values.
    ///         They can be greater than 1.0.  All transformed component
    ///         values are clipped to the interval [0, 255].
    ///     (2) For multiplication with a general 3x3 matrix of constants,
    ///         use pixMultMatrixColor().
    ///
    fn mult_contrast_color(&mut self, r_fact: f32, g_fact: f32, b_fact: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixMultConstantColor(self.as_ptr(), r_fact, g_fact, b_fact) }
            .try_into()
    }
}

impl<T: AsLeptonicaPtr> Enhance for T {}

/// Gamma TRC (tone reproduction curve) mapping
///
///     Input:  [`dst`] (<optional> null or equal to [`src`])
///             [`src`] (8 or 32 bpp; or 2, 4 or 8 bpp with colormap)
///             [`gamma`] (gamma correction; must be > 0.0)
///             [`min`]  (input value that gives 0 for output; can be < 0)
///             [`max`]  (input value that gives 255 for output; can be > 255)
///     Return: [`dst`] always
///
/// Notes:
///     (1) [`dst`] must either be null or equal to [`src`].
///         For in-place operation, set [`dst`] == [`src`]:
///            pixGammaTRC(pixs, pixs, ...);
///         To get a new image, set [`dst`] == null:
///            [`dst`] = pixGammaTRC(NULL, pixs, ...);
///     (2) If [`src`] is colormapped, the colormap is transformed,
///         either in-place or in a copy of [`src`].
///     (3) We use a gamma mapping between minval and maxval.
///     (4) If [`gamma`] < 1.0, the image will appear darker;
///         if [`gamma`] > 1.0, the image will appear lighter;
///     (5) If [`gamma`] = 1.0 and [`min`] = 0 and maxval = 255, no
///         enhancement is performed; return a copy unless in-place,
///         in which case this is a no-op.
///     (6) For color images that are not colormapped, the mapping
///         is applied to each component.
///     (7) [`min`] and [`max`] are not restricted to the interval [0, 255].
///         If [`min`] < 0, an input value of 0 is mapped to a
///         nonzero output.  This will turn black to gray.
///         If [`max`] > 255, an input value of 255 is mapped to
///         an output value less than 255.  This will turn
///         white (e.g., in the background) to gray.
///     (8) Increasing [`min`] darkens the image.
///     (9) Decreasing [`max`] bleaches the image.
///     (10) Simultaneously increasing [`min`] and decreasing [`max`]
///          will darken the image and make the colors more intense;
///          e.g., [`min`] = 50, [`max`] = 200.
///     (11) See numaGammaTRC() for further examples of use.
///
fn gamma_trc_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    gamma: f32,
    min: i32,
    max: i32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixGammaTRC(src, dst, gamma, min, max) }.try_into()
}

///Input: [`dst`] (<optional> null or equal to [`src`]) pixs (8 or 32 bpp; or 2, 4 or 8 bpp with colormap)
/// [`mask`] (<optional> null or 1 bpp) factor (0.0 is no enhancement)
/// Return: [`dst`] always
///Notes: (1) Same as [`set_contrast_trc_inner`] except mapping is optionally over a subset of pixels described by [`mask`].
/// (2) Masking does not work for colormapped images.
/// (3) See [`set_contrast_trc_inner`] for details on how to use the parameters.
fn contrast_trc_masked_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    mask: *mut leptonica_sys::PIX,
    factor: f32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixContrastTRCMasked(dst, src, mask, factor) }.try_into()
}

/// Input: pixd (<optional> null or equal to pixs) pixs (8 or 32 bpp; or 2, 4 or 8 bpp with colormap) factor (0.0 is no enhancement)
/// Return: [`dst`] always
/// Notes: (1) [`dst`] must either be null or equal to [`src`].
/// For in-place operation, set [`dst`] == [`src`] : set_contrast_trc_inner(src, src, ...);
/// To get a new image, set [`dst`] == null: [`dst`] = pixContrastTRC(NULL, src, ...);
/// (2) If [`src`] is colormapped, the colormap is transformed, either in-place or in a copy of [`src`].
/// (3) Contrast is enhanced by mapping each color component using an atan function with maximum slope at 127.
/// Pixels below 127 are lowered in intensity and pixels above 127 are increased.
/// (4) The useful range for the contrast [`factor`] is scaled to be in (0.0 to 1.0), but larger values can also be used.
/// (5) If [`factor`] == 0.0, no enhancement is performed; return a copy unless in-place, in which case this is a no-op.
/// (6) For color images that are not colormapped, the mapping is applied to each component.
fn contrast_trc_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    factor: f32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixContrastTRC(dst, src, factor) }.try_into()
}

/// /*-------------------------------------------------------------*
/// *                     Histogram equalization                  *
/// *-------------------------------------------------------------*/
///        [`dst`]     optional null or equal to [`src`]
///        [`src`]     8 bpp gray, 32 bpp rgb, or colormapped
///        [`fract`]    fraction of equalization movement of pixel values
///        [`factor`]   subsampling factor; integer >= 1
///    return  [`dst`], or NULL on error
///
///Notes:
///     (1) pixd must either be null or equal to pixs.
///         For in-place operation, set pixd == pixs:
///            pixEqualizeTRC(pixs, pixs, ...);
///         To get a new image, set pixd == null:
///            pixd = pixEqualizeTRC(NULL, pixs, ...);
///     (2) In histogram equalization, a tone reproduction curve
///         mapping is used to make the number of pixels at each
///         intensity equal.
///     (3) If fract == 0.0, no equalization is performed; return a copy
///         unless in-place, in which case this is a no-op.
///         If fract == 1.0, equalization is complete.
///     (4) Set the subsampling factor > 1 to reduce the amount of computation.
///     (5) If pixs is colormapped, the colormap is removed and
///         converted to rgb or grayscale.
///     (6) If pixs has color, equalization is done in each channel
///         separately.
///     (7) Note that even if there is a colormap, we can get an
///         in-place operation because the intermediate image pixt
///         is copied back to pixs (which for in-place is the same
///         as pixd).
fn equalize_trc_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    fract: f32,
    factor: i32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixEqualizeTRC(dst, src, fract, factor) }.try_into()
}

///    Input:  [`dst`] (<optional> can be null or equal to [`src`])
///            [`src`] (32 bpp rgb)
///            [`fract`] (between -1.0 and 1.0)
///    Return: [`dst`], or null on error
///
///Notes:
///    (1) [`dst`] must either be null or equal to [`src`].
///        For in-place operation, set [`dst`] == [`src`]:
///           pixEqualizeTRC(pixs, pixs, ...);
///        To get a new image, set pixd == null:
///           pixd = pixEqualizeTRC(NULL, pixs, ...);
///    (1) Use [`fract`] > 0.0 to increase hue value; < 0.0 to decrease it.
///        1.0 (or -1.0) represents a 360 degree rotation; i.e., no change.
///    (2) If no modification is requested ([`fract`] = -1.0 or 0 or 1.0),
///        return a copy unless in-place, in which case this is a no-op.
///
fn modify_hue_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    fract: f32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixModifyHue(dst, src, fract) }.try_into()
}

/// Input: [`destination`] (<optional> can be null, existing or equal to [`src`]) pixs (32 bpp rgb) [`fract`] (between -1.0 and 1.0)
/// Return: [`destination`], or null on error
/// Notes: (1) If [`fract`] > 0.0, it gives the fraction that the pixel saturation is moved from its initial value toward 255.
/// If [`fract`] < 0.0, it gives the fraction that the pixel saturation is moved from its initial value toward 0.
/// The limiting values for [`fract`] = -1.0 (1.0) thus set the saturation to 0 (255).
///     (2) If [`fract`] = 0, no modification is requested; return a copy unless in-place, in which case this is a no-op.
fn modify_saturation_inner(
    src: *mut leptonica_sys::PIX,
    destination: *mut leptonica_sys::PIX,
    fract: f32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixModifySaturation(destination, src, fract) }.try_into()
}

///     pixd     optional can be null, existing or equal to pixs
///     pixs     32 bpp rgb
///     fract    between -1.0 and 1.0
///     return  pixd, or NULL on error
///
///Notes:
///     (1) If fract > 0.0, it gives the fraction that the v-parameter,
///         which is max(r,g,b), is moved from its initial value toward 255.
///         If fract < 0.0, it gives the fraction that the v-parameter
///         is moved from its initial value toward 0.
///         The limiting values for fract = -1.0 (1.0) thus set the
///         v-parameter to 0 (255).
///     (2) If fract = 0, no modification is requested; return a copy
///         unless in-place, in which case this is a no-op.
///     (3) This leaves hue and saturation invariant.
///     (4) See discussion of color-modification methods, in coloring.c.
fn modify_brightness_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    fract: f32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixModifyBrightness(dst, src, fract) }.try_into()
}

/// /*-----------------------------------------------------------------------*
/// *                     Darken gray (unsaturated) pixels
/// *-----------------------------------------------------------------------*/
///     [`dst`]      optional can be null or equal to [`src`]
///     [`src`]     32 bpp rgb
///     [`thresh`]    pixels with max component >= [`thresh`] are unchanged
///     [`sat_limit`]  pixels with saturation >= [`sat_limit`] are unchanged
///     return  [`dst`], or NULL on error
///
///Notes:
///     (1) This darkens gray pixels, by a fraction (sat/%satlimit), where
///         the saturation, sat, is the component difference (max - min).
///         The pixel value is unchanged if sat >= %satlimit.  A typical
///         value of %satlimit might be 40; the larger the value, the
///         more that pixels with a smaller saturation will be darkened.
///     (2) Pixels with max component >= %thresh are unchanged. This can be
///         used to prevent bright pixels with low saturation from being
///         darkened.  Setting thresh == 0 is a no-op; setting %thresh == 255
///         causes the darkening to be applied to all pixels.
///     (3) This function is useful to enhance pixels relative to a
///         gray background.
///     (4) A related function that builds a 1 bpp mask over the gray
///         pixels is pixMaskOverGrayPixels().
fn darken_gray_inner(
    src: *mut leptonica_sys::PIX,
    dst: *mut leptonica_sys::PIX,
    thresh: i32,
    sat_limit: i32,
) -> Result<Pix> {
    unsafe { leptonica_sys::pixDarkenGray(dst, src, thresh, sat_limit) }.try_into()
}

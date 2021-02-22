use super::{AsLeptonicaPtr, Color, Pix, Result};

use std::convert::TryInto;

pub trait Scale1: AsLeptonicaPtr {
    //*********************************************************************************************
    // ================================ Top-level scaling ==========================================
    //*********************************************************************************************

    ///------------------------------------------------------------------
    ///                    Top level scaling dispatcher                  
    ///------------------------------------------------------------------
    ///[pixs]       1, 2, 4, 8, 16 and 32 bpp
    ///[x_factor], [y_factor]
    ///pixd, or NULL on error
    ///
    /// This function scales 32 bpp RGB; 2, 4 or 8 bpp palette color;
    /// 2, 4, 8 or 16 bpp gray; and binary images.
    ///
    /// When the input has palette color, the colormap is removed and
    /// the result is either 8 bpp gray or 32 bpp RGB, depending on whether
    /// the colormap has color entries.  Images with 2, 4 or 16 bpp are
    /// converted to 8 bpp.
    ///
    /// Because pixScale is meant to be a very simple interface to a
    /// number of scaling functions, including the use of unsharp masking,
    /// the type of scaling and the sharpening parameters are chosen
    /// by default.  Grayscale and color images are scaled using one
    /// of five methods, depending on the scale factors:
    ///  1. antialiased subsampling (lowpass filtering followed by
    ///     subsampling, implemented by convolution, for tiny scale factors:
    ///       min(scalex, scaley) < 0.02.
    ///  2. antialiased subsampling (implemented by area mapping, for
    ///     small scale factors:
    ///       max(scalex, scaley) < 0.2 and min(scalex, scaley) >= 0.02.
    ///  3. antialiased subsampling with sharpening, for scale factors
    ///     between 0.2 and 0.7
    ///  4. linear interpolation with sharpening, for scale factors between
    ///     0.7 and 1.4
    ///  5. linear interpolation without sharpening, for scale factors >= 1.4.
    ///
    /// One could use subsampling for scale factors very close to 1.0,
    /// because it preserves sharp edges.  Linear interpolation blurs
    /// edges because the dest pixels will typically straddle two src edge
    /// pixels.  Subsmpling removes entire columns and rows, so the edge is
    /// not blurred.  However, there are two reasons for not doing this.
    /// First, it moves edges, so that a straight line at a large angle to
    /// both horizontal and vertical will have noticeable kinks where
    /// horizontal and vertical rasters are removed.  Second, although it
    /// is very fast, you get good results on sharp edges by applying
    /// a sharpening filter.
    ///
    /// For images with sharp edges, sharpening substantially improves the
    /// image quality for scale factors between about 0.2 and about 2.0.
    /// pixScale uses a small amount of sharpening by default because
    /// it strengthens edge pixels that are weak due to anti-aliasing.
    /// The default sharpening factors are:
    ///     * for scaling factors < 0.7:   sharpfract = 0.2    sharpwidth = 1
    ///     * for scaling factors >= 0.7:  sharpfract = 0.4    sharpwidth = 2
    /// The cases where the sharpening halfwidth is 1 or 2 have special
    /// implementations and are about twice as fast as the general case.
    ///
    /// However, sharpening is computationally expensive, and one needs
    /// to consider the speed-quality tradeoff:
    ///     * For upscaling of RGB images, linear interpolation plus default
    ///       sharpening is about 5 times slower than upscaling alone.
    ///     * For downscaling, area mapping plus default sharpening is
    ///       about 10 times slower than downscaling alone.
    /// When the scale factor is larger than 1.4, the cost of sharpening,
    /// which is proportional to image area, is very large compared to the
    /// incremental quality improvement, so we cut off the default use of
    /// sharpening at 1.4.  Thus, for scale factors greater than 1.4,
    /// pixScale only does linear interpolation.
    ///
    /// In many situations you will get a satisfactory result by scaling
    /// without sharpening: call pixScaleGeneral with %sharpfract = 0.0.
    /// Alternatively, if you wish to sharpen but not use the default
    /// value, first call pixScaleGeneral with %sharpfract = 0.0, and
    /// then sharpen explicitly using pixUnsharpMasking.
    ///
    /// Binary images are scaled to binary by sampling the closest pixel,
    /// without any low-pass filtering averaging of neighboring pixels.
    /// This will introduce aliasing for reductions.  Aliasing can be
    /// prevented by using pixScaleToGray instead.
    ///
    fn scale(&self, x_factor: f32, y_factor: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScale(self.as_ptr(), x_factor, y_factor) }.try_into()
    }

    ///param[in]    pixs
    ///param[in]    delw    change in width, in pixels; 0 means no change
    ///param[in]    delh    change in height, in pixels; 0 means no change
    ///return  pixd, or NULL on error
    fn scale_to_size_rel(&self, dw: i32, dh: i32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleToSizeRel(self.as_ptr(), dw, dh) }.try_into()
    }

    ///param[in]    pixs    1, 2, 4, 8, 16 and 32 bpp
    ///param[in]    wd      target width; use 0 if using height as target
    ///param[in]    hd      target height; use 0 if using width as target
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) The output scaled image has the dimension(s) you specify:
    ///         * To specify the width with isotropic scaling, set %hd = 0.
    ///         * To specify the height with isotropic scaling, set %wd = 0.
    ///         * If both %wd and %hd are specified, the image is scaled
    ///            (in general, anisotropically) to that size.
    ///         * It is an error to set both %wd and %hd to 0.
    fn scale_to_size(&self, wd: u32, hd: u32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleToSize(self.as_ptr(), wd as _, hd as _) }.try_into()
    }

    /// param[in]    pixs         1, 2, 4, 8, 16 and 32 bpp
    /// param[in]    scalex       must be > 0.0
    /// param[in]    scaley       must be > 0.0
    /// param[in]    sharpfract   use 0.0 to skip sharpening
    /// param[in]    sharpwidth   halfwidth of low-pass filter; typ. 1 or 2
    /// return  pixd, or NULL on error
    ///
    /// Notes:
    ///      (1) See pixScale() for usage.
    ///      (2) This interface may change in the future, as other special
    ///          cases are added.
    ///      (3) For tiny scaling factors
    ///            minscale < 0.02:        use a simple lowpass filter
    ///      (4) The actual sharpening factors used depend on the maximum
    ///          of the two scale factors (maxscale):
    ///            maxscale <= 0.2:        no sharpening
    ///            0.2 < maxscale < 1.4:   uses the input parameters
    ///            maxscale >= 1.4:        no sharpening
    ///      (5) To avoid sharpening for grayscale and color images with
    ///          scaling factors between 0.2 and 1.4, call this function
    ///          with %sharpfract == 0.0.
    ///      (6) To use arbitrary sharpening in conjunction with scaling,
    ///          call this function with %sharpfract = 0.0, and follow this
    ///          with a call to pixUnsharpMasking() with your chosen parameters.
    fn scale_general(
        &self,
        scale_x: f32,
        scale_y: f32,
        sharp_fract: f32,
        sharp_width: i32,
    ) -> Result<Pix> {
        unsafe {
            leptonica_sys::pixScaleGeneral(
                self.as_ptr(),
                scale_x,
                scale_y,
                sharp_fract,
                sharp_width,
            )
        }
        .try_into()
    }

    //*********************************************************************************************
    // ======================= Linearly interpreted (usually up-) scaling =========================
    //*********************************************************************************************

    ///param[in]    pixs       2, 4, 8 or 32 bpp; with or without colormap
    ///param[in]    scalex     must be >= 0.7
    ///param[in]    scaley     must be >= 0.7
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) This function should only be used when the scale factors are
    ///         greater than or equal to 0.7, and typically greater than 1.
    ///         If both scale factors are smaller than 0.7, we issue a warning
    ///         and call pixScaleGeneral(), which will invoke area mapping
    ///         without sharpening.
    ///     (2) This works on 2, 4, 8, 16 and 32 bpp images, as well as on
    ///         2, 4 and 8 bpp images that have a colormap.  If there is a
    ///         colormap, it is removed to either gray or RGB, depending
    ///         on the colormap.
    ///     (3) This does a linear interpolation on the src image.
    ///     (4) It dispatches to much faster implementations for
    ///         the special cases of 2x and 4x expansion.
    fn scale_li(&self, scale_x: f32, scale_y: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleLI(self.as_ptr(), scale_x, scale_y) }.try_into()
    }

    ///param[in]    pixs       32 bpp, representing rgb
    ///param[in]    scalex     must be >= 0.7
    ///param[in]    scaley     must be >= 0.7
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) If both scale factors are smaller than 0.7, we issue a warning
    ///         and call pixScaleGeneral(), which will invoke area mapping
    ///         without sharpening.  This is particularly important for
    ///         document images with sharp edges.
    ///     (2) For the general case, it's about 4x faster to manipulate
    ///         the color pixels directly, rather than to make images
    ///         out of each of the 3 components, scale each component
    ///         using the pixScaleGrayLI(), and combine the results back
    ///         into an rgb image.
    fn scale_color_li(&self, scale_x: f32, scale_y: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleColorLI(self.as_ptr(), scale_x, scale_y) }.try_into()
    }

    ///\param[in]    pixs    32 bpp, representing rgb
    ///\return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) This is a special case of linear interpolated scaling,
    ///         for 2x upscaling.  It is about 8x faster than using
    ///         the generic pixScaleColorLI(), and about 4x faster than
    ///         using the special 2x scale function pixScaleGray2xLI()
    ///         on each of the three components separately.
    fn scale_color_2x_li(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleColor2xLI(self.as_ptr()) }.try_into()
    }

    ///param[in]    pixs    32 bpp, representing rgb
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) This is a special case of color linear interpolated scaling,
    ///         for 4x upscaling.  It is about 3x faster than using
    ///         the generic pixScaleColorLI().
    ///     (2) This scales each component separately, using pixScaleGray4xLI().
    ///         It would be about 4x faster to inline the color code properly,
    ///         in analogy to scaleColor4xLILow(), and I leave this as
    ///         an exercise for someone who really needs it.
    fn scale_color_4x_li(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleColor4xLI(self.as_ptr()) }.try_into()
    }

    ///param[in]    pixs       8 bpp grayscale, no cmap
    ///param[in]    scalex     must be >= 0.7
    ///param[in]    scaley     must be >= 0.7
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) This function is appropriate for upscaling magnification, where the
    ///         scale factor is > 1, as well as for a small amount of downscaling
    ///         reduction, with scale factor >= 0.7.  If the scale factor is < 0.7,
    ///         the best result is obtained by area mapping.
    ///     (2) Here are some details:
    ///         - For each pixel in the dest, this does a linear
    ///           interpolation of 4 neighboring pixels in the src.
    ///           Specifically, consider the UL corner of src and
    ///           dest pixels.  The UL corner of the dest falls within
    ///           a src pixel, whose four corners are the UL corners
    ///           of 4 adjacent src pixels.  The value of the dest
    ///           is taken by linear interpolation using the values of
    ///           the four src pixels and the distance of the UL corner
    ///           of the dest from each corner.
    ///         - If the image is expanded so that the dest pixel is
    ///           smaller than the src pixel, such interpolation
    ///           is a reasonable approach.  This interpolation is
    ///           also good for a small image reduction factor that
    ///           is not more than a 2x reduction.
    ///         - The linear interpolation algorithm for scaling is
    ///           identical in form to the area-mapping algorithm
    ///           for grayscale rotation.  The latter corresponds to a
    ///           translation of each pixel without scaling.
    ///         - This function is NOT optimal if the scaling involves
    ///           a large reduction.  If the image is significantly
    ///           reduced, so that the dest pixel is much larger than
    ///           the src pixels, this interpolation, which is over src
    ///           pixels only near the UL corner of the dest pixel,
    ///           is not going to give a good area-mapping average.
    ///           Because area mapping for image scaling is considerably
    ///           more computationally intensive than linear interpolation,
    ///           we choose not to use it.  For large image reduction,
    ///           linear interpolation over adjacent src pixels
    ///           degenerates asymptotically to subsampling.  But
    ///           subsampling without a low-pass pre-filter causes
    ///           aliasing by the nyquist theorem.  To avoid aliasing,
    ///           a low-pass filter e.g., an averaging filter of
    ///           size roughly equal to the dest pixel i.e., the reduction
    ///           factor should be applied to the src before subsampling.
    ///         - As an alternative to low-pass filtering and subsampling
    ///           for large reduction factors, linear interpolation can
    ///           also be done between the widely separated src pixels in
    ///           which the corners of the dest pixel lie.  This also is
    ///           not optimal, as it samples src pixels only near the
    ///           corners of the dest pixel, and it is not implemented.
    fn scale_gray_li(&self, scale_x: f32, scale_y: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleGrayLI(self.as_ptr(), scale_x, scale_y) }.try_into()
    }

    ///param[in]    pixs    8 bpp grayscale, not cmapped
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) This is a special case of gray linear interpolated scaling,
    ///         for 2x upscaling.  It is about 6x faster than using
    ///         the generic pixScaleGrayLI().
    fn scale_gray_2x_li(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleGray2xLI(self.as_ptr()) }.try_into()
    }

    ///param[in]    pixs    8 bpp grayscale, not cmapped
    ///return  pixd, or NULL on error
    ///
    ///Notes:
    ///     (1) This is a special case of gray linear interpolated scaling,
    ///         for 4x upscaling.  It is about 12x faster than using
    ///         the generic pixScaleGrayLI().
    fn scale_gray_4x_li(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleGray4xLI(self.as_ptr()) }.try_into()
    }

    //*********************************************************************************************
    // =======================  Fast integer factor subsampling RGB to gray =======================
    //*********************************************************************************************

    ///param[in]    pixs     32 bpp rgb
    ///param[in]    factor   integer reduction factor >= 1
    ///param[in]    color    one of COLOR_RED, COLOR_GREEN, COLOR_BLUE
    ///return  pixd 8 bpp, or NULL on error
    ///
    ///Notes:
    ///     (1) This does simultaneous subsampling by an integer factor and
    ///         extraction of the color from the RGB pix.
    ///     (2) It is designed for maximum speed, and is used for quickly
    ///         generating a downsized grayscale image from a higher resolution
    ///         RGB image.  This would typically be used for image analysis.
    ///     (3) The standard color byte order (RGBA) is assumed.
    fn scale_rgb_to_gray_fast(&self, factor: u32, color: Color) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleRGBToGrayFast(self.as_ptr(), factor as _, color as _) }
            .try_into()
    }

    ///param[in]    pixs     32 bpp RGB
    ///param[in]    factor   integer reduction factor >= 1
    ///param[in]    thresh   binarization threshold
    ///return  pixd 1 bpp, or NULL on error
    ///
    ///Notes:
    ///     (1) This does simultaneous subsampling by an integer factor and
    ///         conversion from RGB to gray to binary.
    ///     (2) It is designed for maximum speed, and is used for quickly
    ///         generating a downsized binary image from a higher resolution
    ///         RGB image.  This would typically be used for image analysis.
    ///     (3) It uses the green channel to represent the RGB pixel intensity.
    fn scale_rgb_to_binary_fast(&self, factor: u32, thresh: i32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleRGBToBinaryFast(self.as_ptr(), factor as _, thresh) }
            .try_into()
    }

    ///\param[in]    pixs     8 bpp grayscale
    ///\param[in]    factor   integer reduction factor >= 1
    ///\param[in]    thresh   binarization threshold
    ///\return  pixd 1 bpp, or NULL on error
    ///
    ///Notes:
    ///     (1) This does simultaneous subsampling by an integer factor and
    ///         thresholding from gray to binary.
    ///     (2) It is designed for maximum speed, and is used for quickly
    ///         generating a downsized binary image from a higher resolution
    ///         gray image.  This would typically be used for image analysis.
    fn scale_gray_to_binary_fast(&self, factor: u32, thresh: i32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleGrayToBinaryFast(self.as_ptr(), factor as _, thresh) }
            .try_into()
    }

    //*********************************************************************************************
    // ======================= Downscaling with (antialias) smoothing =============================
    //*********************************************************************************************

    ///------------------------------------------------------------------
    ///               Downscaling with (antialias) smoothing             
    ///------------------------------------------------------------------
    ///    pix       2, 4, 8 or 32 bpp; and 2, 4, 8 bpp with colormap
    ///    scalex    must be < 0.7
    ///    scaley    must be < 0.7
    ///    return  pixd, or NULL on error
    ///Notes:
    ///     (1) This function should only be used when the scale factors are less
    ///         than 0.7.  If either scale factor is >= 0.7, issue a warning
    ///         and call pixScaleGeneral(), which will invoke linear interpolation
    ///         without sharpening.
    ///     (2) This works only on 2, 4, 8 and 32 bpp images, and if there is
    ///         a colormap, it is removed by converting to RGB.
    ///     (3) It does simple (flat filter) convolution, with a filter size
    ///         commensurate with the amount of reduction, to avoid antialiasing.
    ///     (4) It does simple subsampling after smoothing, which is appropriate
    ///         for this range of scaling.  Linear interpolation gives essentially
    ///         the same result with more computation for these scale factors,
    ///         so we don't use it.
    ///     (5) The result is the same as doing a full block convolution followed by
    ///         subsampling, but this is faster because the results of the block
    ///         convolution are only computed at the subsampling locations.
    ///         In fact, the computation time is approximately independent of
    ///         the scale factor, because the convolution kernel is adjusted
    ///         so that each source pixel is summed approximately once.
    fn scale_smooth(&mut self, x_factor: f32, y_factor: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleSmooth(self.as_ptr(), x_factor, y_factor) }.try_into()
    }

    ///param[in]    pixs            32 bpp rgb
    ///param[in]    rwt, gwt, bwt   must sum to 1.0
    ///return  pixd, 8 bpp, 2x reduced, or NULL on error
    ///
    fn scale_rgb_to_gray_2(&self, rwt: f32, gwt: f32, bwt: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixScaleRGBToGray2(self.as_ptr(), rwt, gwt, bwt) }.try_into()
    }
}

impl<T: AsLeptonicaPtr> Scale1 for T {}

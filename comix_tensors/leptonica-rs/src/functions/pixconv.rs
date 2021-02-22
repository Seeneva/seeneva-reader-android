use super::{AsLeptonicaPtr, Choose, Pix, Result};

use std::convert::TryInto;

pub trait PixConv: AsLeptonicaPtr {
    //*********************************************************************************************
    // =================== Conversion from 8 bpp grayscale to 1, 2 4 and 8 bpp  ===================
    //*********************************************************************************************

    ///\param[in]    pixs       8 bpp grayscale
    ///\param[in]    d          destination depth: 1, 2, 4 or 8
    ///\param[in]    nlevels    number of levels to be used for colormap
    ///\param[in]    cmapflag   1 if makes colormap; 0 otherwise
    ///\return  pixd thresholded with standard dest thresholds,
    ///             or NULL on error
    ///
    ///Notes:
    ///     (1) This uses, by default, equally spaced "target" values
    ///         that depend on the number of levels, with thresholds
    ///         halfway between.  For N levels, with separation (N-1)/255,
    ///         there are N-1 fixed thresholds.
    ///     (2) For 1 bpp destination, the number of levels can only be 2
    ///         and if a cmap is made, black is (0,0,0) and white
    ///         is (255,255,255), which is opposite to the convention
    ///         without a colormap.
    ///     (3) For 1, 2 and 4 bpp, the nlevels arg is used if a colormap
    ///         is made; otherwise, we take the most significant bits
    ///         from the src that will fit in the dest.
    ///     (4) For 8 bpp, the input pixs is quantized to nlevels.  The
    ///         dest quantized with that mapping, either through a colormap
    ///         table or directly with 8 bit values.
    ///     (5) Typically you should not use make a colormap for 1 bpp dest.
    ///     (6) This is not dithering.  Each pixel is treated independently.
    fn threshold_8(&self, depth: u16, n_levels: u32, cmap: bool) -> Result<Pix> {
        unsafe { leptonica_sys::pixThreshold8(self.as_ptr(), depth as _, n_levels as _, cmap as _) }
            .try_into()
    }

    //*********************************************************************************************
    // ======================= Conversion from RGB color to grayscale =============================
    //*********************************************************************************************

    /// Input: pix (32 bpp RGB) rwt, gwt, bwt (non-negative; these should add to 1.0, or use 0.0 for default)
    /// Return: 8 bpp pix, or null on error
    ///Notes: (1) Use a weighted average of the RGB values.
    fn convert_rgb_to_gray(&self, rwt: f32, gwt: f32, bwt: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertRGBToGray(self.as_ptr(), rwt, gwt, bwt) }.try_into()
    }

    /// Input: pix (32 bpp RGB) Return: 8 bpp pix,
    /// Notes: (1) This function should be used if speed of conversion is paramount,
    /// and the green channel can be used as a fair representative of the RGB intensity.
    /// It is several times faster than [`convert_rgb_to_gray`].
    /// (2) To combine RGB to gray conversion with subsampling, use pixScaleRGBToGrayFast() instead.
    fn convert_rgb_to_gray_fast(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertRGBToGrayFast(self.as_ptr()) }.try_into()
    }

    /// Input: pix (32 bpp RGB) type (L_CHOOSE_MIN or L_CHOOSE_MAX) Return: 8 bpp pix, or null on error
    /// Notes: (1) chooses among the 3 color components for each pixel
    /// (2) This is useful when looking for the maximum deviation of a component from either 0 or 255.
    /// For finding the deviation of a single component, it is more sensitive than using a weighted average.
    fn convert_rgb_to_gray_min_max(&self, choose: Choose) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertRGBToGrayMinMax(self.as_ptr(), choose as _) }.try_into()
    }

    /// [`ref_val`]  between 1 and 255; typ. less than 128
    /// return  pixd 8 bpp
    ///
    /// Notes:
    ///    (1) This returns the max component value, boosted by
    ///          the saturation. The maximum boost occurs where
    ///          the maximum component value is equal to some reference value.
    ///          This particular weighting is due to Dany Qumsiyeh.
    ///      (2) For gray pixels (zero saturation), this returns
    ///          the intensity of any component.
    ///      (3) For fully saturated pixels ('fullsat'), this rises linearly
    ///          with the max value and has a slope equal to 255 divided
    ///          by the reference value; for a max value greater than
    ///          the reference value, it is clipped to 255.
    ///      (4) For saturation values in between, the output is a linear
    ///          combination of (2) and (3), weighted by saturation.
    ///          It falls between these two curves, and does not exceed 255.
    ///      (5) This can be useful for distinguishing an object that has nonzero
    ///          saturation from a gray background.  For this, the refval
    ///          should be chosen near the expected value of the background,
    ///          to achieve maximum saturation boost there.
    fn convert_rgb_to_gray_sat_boost(&self, ref_val: u8) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertRGBToGraySatBoost(self.as_ptr(), ref_val as _) }
            .try_into()
    }

    ///    pixs        32 bpp RGB
    ///    rc, gc, bc  arithmetic factors; can be negative
    ///    return  8 bpp pix, or NULL on error
    ///
    ///Notes:
    ///     (1) This converts to gray using an arbitrary linear combination
    ///         of the rgb color components.  It differs from pixConvertToGray(),
    ///         which uses only positive coefficients that sum to 1.
    ///     (2) The gray output values are clipped to 0 and 255.
    fn convert_rgb_to_gray_arb(&self, rc: f32, gc: f32, bc: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertRGBToGrayArb(self.as_ptr(), rc, gc, bc) }.try_into()
    }

    //*********************************************************************************************
    // =========================== Top-level conversion to 8 bpp  =================================
    //*********************************************************************************************

    ///\param[in]    pixs      1, 2, 4, 8, 16, 24 or 32 bpp
    ///\param[in]    cmapflag  TRUE if pixd is to have a colormap; FALSE otherwise
    ///\return  pixd 8 bpp, or NULL on error
    ///
    ///<pre>
    ///Notes:
    ///     (1) This is a top-level function, with simple default values
    ///         for unpacking.
    ///     (2) The result, pixd, is made with a colormap if specified.
    ///         It is always a new image -- never a clone.  For example,
    ///         if d == 8, and cmapflag matches the existence of a cmap
    ///         in pixs, the operation is lossless and it returns a copy.
    ///     (3) The default values used are:
    ///         ~ 1 bpp: val0 = 255, val1 = 0
    ///         ~ 2 bpp: 4 bpp:  even increments over dynamic range
    ///         ~ 8 bpp: lossless if cmap matches cmapflag
    ///         ~ 16 bpp: use most significant byte
    ///     (4) If 24 bpp or 32 bpp RGB, this is converted to gray.
    ///         For color quantization, you must specify the type explicitly,
    ///         using the color quantization code.
    fn convert_to_8(&self, cmap: bool) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertTo8(self.as_ptr(), cmap as _) }.try_into()
    }

    ///\param[in]    pixs      1, 2, 4, 8, 16 or 32 bpp
    ///\param[in]    factor    submsampling factor; integer >= 1
    ///\param[in]    cmapflag  TRUE if pixd is to have a colormap; FALSE otherwise
    ///\return  pixd 8 bpp, or NULL on error
    ///
    ///Notes:
    ///     (1) This is a fast, quick/dirty, top-level converter.
    ///     (2) See pixConvertTo8() for default values.
    fn convert_to_8_by_sampling(&self, factor: u32, cmap: bool) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertTo8BySampling(self.as_ptr(), factor as _, cmap as _) }
            .try_into()
    }

    ///\param[in]    pixs    1, 2, 4, 8, 16 or 32 bpp
    ///\param[in]    dither  1 to dither if necessary; 0 otherwise
    ///\return  pixd 8 bpp, cmapped, or NULL on error
    ///
    ///Notes:
    ///     (1) This is a top-level function, with simple default values
    ///         for unpacking.
    ///     (2) The result, pixd, is always made with a colormap.
    ///     (3) If d == 8, the operation is lossless and it returns a copy.
    ///     (4) The default values used for increasing depth are:
    ///         ~ 1 bpp: val0 = 255, val1 = 0
    ///         ~ 2 bpp: 4 bpp:  even increments over dynamic range
    ///     (5) For 16 bpp, use the most significant byte.
    ///     (6) For 32 bpp RGB, use octcube quantization with optional dithering.
    fn convert_to_8_colormap(&self, dither: bool) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertTo8Colormap(self.as_ptr(), dither as _) }.try_into()
    }

    //*********************************************************************************************
    // =========================== Top-level conversion to 32 bpp (RGB)  ==========================
    //*********************************************************************************************

    ///\param[in]    pixs    1, 2, 4, 8, 16, 24 or 32 bpp
    ///\return  pixd 32 bpp, or NULL on error
    ///
    /// Usage: Top-level function, with simple default values for unpacking.
    ///     1 bpp:  val0 = 255, val1 = 0
    ///             and then replication into R, G and B components
    ///     2 bpp:  if colormapped, use the colormap values; otherwise,
    ///             use val0 = 0, val1 = 0x55, val2 = 0xaa, val3 = 255
    ///             and replicate gray into R, G and B components
    ///     4 bpp:  if colormapped, use the colormap values; otherwise,
    ///             replicate 2 nybs into a byte, and then into R,G,B components
    ///     8 bpp:  if colormapped, use the colormap values; otherwise,
    ///             replicate gray values into R, G and B components
    ///     16 bpp: replicate MSB into R, G and B components
    ///     24 bpp: unpack the pixels, maintaining word alignment on each scanline
    ///     32 bpp: makes a copy
    ///
    ///Notes:
    ///     (1) Never returns a clone of pixs.
    fn convert_to_32(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertTo32(self.as_ptr()) }.try_into()
    }

    ///\param[in]    pixs    8 bpp
    ///\return  32 bpp rgb pix, or NULL on error
    ///
    ///<pre>
    ///Notes:
    ///     (1) If there is no colormap, replicates the gray value
    ///         into the 3 MSB of the dest pixel.
    fn convert_8_to_32(&self) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvert8To32(self.as_ptr()) }.try_into()
    }

    //*********************************************************************************************
    // ======================== Conversion from grayscale to false color  =========================
    //*********************************************************************************************

    ///\param[in]    pixs    8 or 16 bpp grayscale
    ///\param[in]    gamma   (factor) 0.0 or 1.0 for default; > 1.0 for brighter;
    ///                      2.0 is quite nice
    ///\return  pixd 8 bpp with colormap, or NULL on error
    ///
    ///Notes:
    ///     (1) For 8 bpp input, this simply adds a colormap to the input image.
    ///     (2) For 16 bpp input, it first converts to 8 bpp, using the MSB,
    ///         and then adds the colormap.
    ///     (3) The colormap is modeled after the Matlab "jet" configuration.
    fn convert_gray_to_false_color(&self, gamma: f32) -> Result<Pix> {
        unsafe { leptonica_sys::pixConvertGrayToFalseColor(self.as_ptr(), gamma) }.try_into()
    }
}

impl<T: AsLeptonicaPtr> PixConv for T {}

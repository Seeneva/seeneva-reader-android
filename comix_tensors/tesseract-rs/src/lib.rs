use std::convert::{TryFrom, TryInto};
use std::ffi::{CStr, CString};
use std::io::Read;
use std::marker::PhantomData;
use std::os::raw::c_int;
use std::path::Path;

use tesseract_sys;

pub use error::*;
use leptonica_rs::AsLeptonicaPtr;
use std::ops::{Deref, DerefMut};
use wrappers::*;

mod error;
mod wrappers;

/// Provided leptonica
pub mod leptonica {
    pub use leptonica_rs::prelude::*;
}

/// Return Tesseract library version
#[track_caller]
pub fn version() -> &'static str {
    let c_str = unsafe { CStr::from_ptr(tesseract_sys::TessVersion()) };

    c_str.to_str().expect("Can't get Tesseract version")
}

/// Tesseract API
#[derive(Debug)]
pub struct Tesseract {
    api: *mut tesseract_sys::TessBaseAPI,
}

impl Tesseract {
    ///Create new Tesseract Api instance
    #[track_caller]
    fn new() -> Self {
        unsafe { tesseract_sys::TessBaseAPICreate() }
            .try_into()
            .expect("Tesseract Api cannot be null!")
    }

    /// Wrap raw pointer
    fn wrap(ptr: *mut tesseract_sys::TessBaseAPI) -> Result<Self> {
        if ptr.is_null() {
            Err(Error::new_sys_error(
                SysErrorKind::Null,
                Some("Tesseract pointer is null"),
            ))
        } else {
            Ok(Tesseract { api: ptr })
        }
    }

    /// Init Tesseract from path to the directory with tessdata
    #[track_caller]
    pub fn from_path(
        tessdata_dir: impl AsRef<Path>,
        lang: impl AsRef<str>,
        oem: EngineMode,
    ) -> Result<Self> {
        let tessdata_dir = tessdata_dir.as_ref();

        if !tessdata_dir.exists() || !tessdata_dir.is_dir() {
            panic!("Provided tessdata dir should be exist and should be a directory");
        }

        let tessdata_dir_c = if cfg!(unix) {
            use std::os::unix::ffi::OsStrExt;
            CString::new(tessdata_dir.as_os_str().as_bytes())
        } else {
            CString::new(
                tessdata_dir
                    .as_os_str()
                    .to_str()
                    .expect("Can't get tessdata dir path"),
            )
        }
        .expect("Can't convert tessdata dit to C string");

        let lang_c = CString::new(lang.as_ref()).expect("Can't convert language to C string");

        let tess = Self::new();

        let init_result = unsafe {
            tesseract_sys::TessBaseAPIInit2(
                tess.api,
                tessdata_dir_c.as_ptr(),
                lang_c.as_ptr(),
                oem as _,
            )
        };

        if init_result != 0 {
            Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Some(format!(
                    "Can't init from the path: {}",
                    tessdata_dir.display()
                )),
            ))
        } else {
            Ok(tess)
        }
    }

    /// Init Tesseract from tessdata file
    #[track_caller]
    pub fn from_reader(
        data: &mut impl Read,
        lang: impl AsRef<str>,
        oem: EngineMode,
    ) -> Result<Self> {
        let mut buf = vec![];

        data.read_to_end(&mut buf)?;

        Self::from_data(buf.as_slice(), lang, oem)
    }

    /// Init Tesseract from RAW tessdata file
    #[track_caller]
    pub fn from_data(data: &[u8], lang: impl AsRef<str>, oem: EngineMode) -> Result<Self> {
        let lang = CString::new(lang.as_ref()).expect("Can't convert language to C string");

        let tess = Self::new();

        let init_result = unsafe {
            tesseract_sys::TessBaseAPIInitExtra(
                tess.api,
                data.as_ptr() as _,
                data.len() as _,
                lang.as_ptr(),
                oem as _,
                std::ptr::null_mut() as *mut _,
                0 as _,
            )
        };

        if init_result != 0 {
            Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Some("Can't init from data"),
            ))
        } else {
            Ok(tess)
        }
    }

    /// Set the value of an internal "parameter."
    /// Supply the name of the parameter and the value as a string, just as
    /// you would in a config file.
    /// Returns false if the name lookup failed.
    /// Eg set_variable("tessedit_char_blacklist", "xyz"); to ignore x, y and z.
    /// Or set_variable("classify_bln_numeric_mode", "1"); to set numeric-only mode.
    /// `set_variable` may be used before Init, but settings will revert to
    /// defaults on `end`.
    /// https://tesseract-ocr.github.io/tessapi/4.0.0/a02358.html#a8a4f92c5ddac8da57ca85e0a2cd0f5ef
    #[track_caller]
    pub fn set_variable<K, V>(&mut self, key: K, value: V) -> bool
    where
        K: AsRef<str>,
        V: AsRef<str>,
    {
        let key = CString::new(key.as_ref()).expect("Can't convert key to C string");
        let value = CString::new(value.as_ref()).expect("Can't convert value to C string");

        (unsafe {
            tesseract_sys::TessBaseAPISetVariable(self.api, key.as_ptr() as _, value.as_ptr() as _)
        }) != 0
    }

    /// Set multiple variables at once
    pub fn set_variables<I, K, V>(&mut self, iter: I)
    where
        I: IntoIterator<Item = (K, V)>,
        K: AsRef<str>,
        V: AsRef<str>,
    {
        for (key, value) in iter {
            self.set_variable(key, value);
        }
    }

    /// Returns the languages string used in the last valid initialization.
    /// If the last initialization specified "deu+hin" then that will be
    /// returned. If hin loaded eng automatically as well, then that will
    /// not be included in this list
    #[track_caller]
    pub fn init_language(&self) -> &str {
        let c_lang =
            unsafe { CStr::from_ptr(tesseract_sys::TessBaseAPIGetInitLanguagesAsString(self.api)) };

        c_lang
            .to_str()
            .expect("Can't convert language from C string")
    }

    /// Set the current page segmentation mode
    pub fn set_segmentation_mode(&mut self, seg_mode: PageSegMode) {
        unsafe {
            tesseract_sys::TessBaseAPISetPageSegMode(self.api, seg_mode as _);
        }
    }

    /// Return the current page segmentation mode.
    pub fn segmentation_mode(&self) -> PageSegMode {
        unsafe { tesseract_sys::TessBaseAPIGetPageSegMode(self.api) }.into()
    }

    /// Recognize a rectangle from an image and return the result as a string.
    /// May be called many times for a single Init.
    /// Currently has no error checking.
    /// Greyscale of 8 and color of 24 or 32 bits per pixel may be given.
    /// Palette color images will not work properly and must be converted to
    /// 24 bit.
    /// Binary images of 1 bit per pixel may also be given but they must be
    /// byte packed with the MSB of the first byte being the first pixel, and a
    /// 1 represents WHITE.
    ///
    /// Note that `rectangle` is the simplified convenience interface.
    /// For advanced uses, use `set_image`, (optionally) `set_rectangle`, Recognize,
    /// and one or more of the Get*Text functions.
    pub fn rect(
        &mut self,
        img: &[u8],
        width: u32,
        bytes_per_pixel: u8,
        rect: impl Into<Rect>,
    ) -> Text {
        let rect = rect.into();

        unsafe {
            tesseract_sys::TessBaseAPIRect(
                self.api,
                img.as_ptr() as _,
                bytes_per_pixel as _,
                (width * bytes_per_pixel as u32) as _,
                rect.left as _,
                rect.top as _,
                rect.width as _,
                rect.height as _,
            )
        }
        .into()
    }

    /// Provide an image for Tesseract to recognize.
    /// Copies the image buffer and converts to Pix.
    /// [`set_image`] clears all recognition results,
    /// and sets the rectangle to the full image,
    /// so it may be followed immediately by a GetUTF8Text,
    /// and it will automatically perform recognition.
    pub fn set_image_raw(&mut self, buf: &[u8], width: u32, height: u32, bytes_per_pixel: u8) {
        unsafe {
            tesseract_sys::TessBaseAPISetImage(
                self.api,
                buf.as_ptr() as _,
                width as _,
                height as _,
                bytes_per_pixel as _,
                (width * bytes_per_pixel as u32) as _,
            )
        }
    }

    /// Provide an image for Tesseract to recognize. As with SetImage above,
    /// Tesseract takes its own copy of the image, so it need not persist until
    /// after Recognize.
    pub fn set_image_pix(&mut self, pix: &leptonica::Pix) {
        unsafe { tesseract_sys::TessBaseAPISetImage2(self.api, pix.as_ptr()) }
    }

    /// Recognize the image from SetAndThresholdImage, generating Tesseract
    /// internal structures.
    /// Optional. The Get*Text functions below will call Recognize if needed.
    /// After Recognize, the output is kept internally until the next SetImage.
    pub fn recognise(&mut self) -> Result<()> {
        let result = unsafe { tesseract_sys::TessBaseAPIRecognize(self.api, std::ptr::null_mut()) };

        if result == 0 {
            Ok(())
        } else {
            Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Some("Can't recognise"),
            ))
        }
    }

    /// Get a reading-order iterator to the results of LayoutAnalysis and/or
    /// Recognize. The returned iterator must be deleted after use.
    /// WARNING! This class points to data held within the TessBaseAPI class, and
    /// therefore can only be used while the TessBaseAPI class still exists and
    /// has not been subjected to a call of Init, SetImage, Recognize, Clear, End
    /// DetectOS, or anything else that changes the internal PAGE_RES.
    pub fn result_iterator(&mut self) -> Option<ResultIterator> {
        unsafe { tesseract_sys::TessBaseAPIGetIterator(self.api) }
            .try_into()
            .ok()
    }

    /// Get a copy of the internal thresholded image from Tesseract.
    /// May be called any time after `set_image`, or after `rect`.
    pub fn get_thresholded_image(&self) -> Option<leptonica::Pix> {
        unsafe { tesseract_sys::TessBaseAPIGetThresholdedImage(self.api) }
            .try_into()
            .ok()
    }

    ///Set the resolution of the source image in pixels per inch so font size
    ///information can be calculated in results.  Call this after `set_image`.
    pub fn set_resolution(&mut self, ppi: u32) {
        unsafe {
            tesseract_sys::TessBaseAPISetSourceResolution(self.api, ppi as _);
        }
    }

    /// Restrict recognition to a sub-rectangle of the image. Call after SetImage.
    /// Each SetRectangle clears the recogntion results so multiple rectangles
    /// can be recognized with the same image.
    pub fn set_rectangle(&mut self, rect: impl Into<Rect>) {
        let rect = rect.into();

        unsafe {
            tesseract_sys::TessBaseAPISetRectangle(
                self.api,
                rect.left as _,
                rect.top as _,
                rect.width as _,
                rect.height as _,
            );
        }
    }

    /// Return recognized text
    pub fn get_text(&mut self) -> Option<Text> {
        let txt_ptr = unsafe { tesseract_sys::TessBaseAPIGetUTF8Text(self.api) };

        if txt_ptr.is_null() {
            None
        } else {
            Some(txt_ptr.into())
        }
    }

    /// Returns the (average) confidence value between 0 and 100
    pub fn mean_text_conf(&mut self) -> u32 {
        (unsafe { tesseract_sys::TessBaseAPIMeanTextConf(self.api) }) as _
    }

    /// Returns all word confidences (between 0 and 100)
    /// The number of confidences should correspond to the number of space-
    /// delimited words in `get_text`.
    pub fn all_word_conf(&mut self) -> Option<Array<c_int, u32>> {
        let ptr = unsafe { tesseract_sys::TessBaseAPIAllWordConfidences(self.api) };

        if ptr.is_null() {
            //set image first!
            None
        } else {
            Some(ptr.into())
        }
    }

    /// Free up recognition results and any stored image data, without actually
    /// freeing any recognition data that would be time-consuming to reload.
    /// Afterwards, you must call `set_image` or `rect` before doing
    /// any Recognize or Get* operation.
    fn clear(&mut self) {
        unsafe {
            tesseract_sys::TessBaseAPIClear(self.api);
        }
    }

    /// Close down tesseract and free up all memory. `end` is equivalent to
    /// destructing and reconstructing your `Tesseract`.
    /// Once `end` has been used, none of the other API functions may be used
    /// other than Init and anything declared above it in the class definition.
    fn end(&mut self) {
        unsafe {
            tesseract_sys::TessBaseAPIEnd(self.api);
        }
    }
}

unsafe impl Send for Tesseract {}

impl Drop for Tesseract {
    fn drop(&mut self) {
        unsafe {
            tesseract_sys::TessBaseAPIDelete(self.api);
        }
    }
}

impl TryFrom<*mut tesseract_sys::TessBaseAPI> for Tesseract {
    type Error = Error;

    fn try_from(value: *mut tesseract_sys::TessBaseAPI) -> std::result::Result<Self, Self::Error> {
        Self::wrap(value)
    }
}

#[derive(Debug)]
pub struct ResultIterator<'a> {
    api: *mut tesseract_sys::TessResultIterator,
    pd: PhantomData<&'a ()>,
}

impl<'a> ResultIterator<'a> {
    fn new(ptr: *mut tesseract_sys::TessResultIterator) -> Result<Self> {
        if ptr.is_null() {
            Err(Error::new_sys_error(
                SysErrorKind::Null,
                Some("Tesseract result iterator cannot be null"),
            ))
        } else {
            Ok(ResultIterator {
                api: ptr,
                pd: PhantomData,
            })
        }
    }

    /// Iterate over provided [`level`]
    pub fn as_iter<'b>(
        &'b mut self,
        level: PageIteratorLevel,
    ) -> ResultIteratorInner<'a, 'b> {
        ResultIteratorInner::new(self, level)
    }

    /// Move to the next word in the iterator using provided [`level`]
    pub fn next(&mut self, level: PageIteratorLevel) -> bool {
        (unsafe { tesseract_sys::TessResultIteratorNext(self.api, level as _) }) == 1
    }

    /// Get current text in the iterator if any
    pub fn get_text(&self, level: PageIteratorLevel) -> Option<Text> {
        let ptr = unsafe { tesseract_sys::TessResultIteratorGetUTF8Text(self.api, level as _) };

        if ptr.is_null() {
            None
        } else {
            Some(ptr.into())
        }
    }

    /// Get current text confidence
    pub fn get_conf(&self, level: PageIteratorLevel) -> f32 {
        unsafe { tesseract_sys::TessResultIteratorConfidence(self.api, level as _) }
    }

    pub fn word_is_numeric(&self) -> bool {
        (unsafe { tesseract_sys::TessResultIteratorWordIsNumeric(self.api) }) == 1
    }

    pub fn word_is_from_dictionary(&self) -> bool {
        (unsafe { tesseract_sys::TessResultIteratorWordIsFromDictionary(self.api) }) == 1
    }
}

impl TryFrom<*mut tesseract_sys::TessResultIterator> for ResultIterator<'_> {
    type Error = Error;

    fn try_from(
        ptr: *mut tesseract_sys::TessResultIterator,
    ) -> std::result::Result<Self, Self::Error> {
        Self::new(ptr)
    }
}

impl Drop for ResultIterator<'_> {
    fn drop(&mut self) {
        unsafe { tesseract_sys::TessResultIteratorDelete(self.api) }
    }
}

/// Wrapper to implement Rust Iterator
#[derive(Debug)]
pub struct ResultIteratorInner<'a, 'b>{
    inner: &'b mut ResultIterator<'a>,
    level: PageIteratorLevel,
}

impl<'a, 'b> ResultIteratorInner<'a, 'b>{
    fn new(inner: &'b mut ResultIterator<'a>, level: PageIteratorLevel) -> Self{
        ResultIteratorInner{
            inner,
            level
        }
    }
}

impl<'a> Deref for ResultIteratorInner<'a, '_> {
    type Target = ResultIterator<'a>;

    fn deref(&self) -> &Self::Target {
        &self.inner
    }
}

impl DerefMut for ResultIteratorInner<'_, '_> {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.inner
    }
}

impl Iterator for ResultIteratorInner<'_, '_> {
    type Item = (Text, f32);

    fn next(&mut self) -> Option<Self::Item> {
        let word = self.get_text(self.level)?;
        let word_conf = self.get_conf(self.level);

        self.inner.next(self.level);

        Some((word, word_conf))
    }
}

/// Represent Tesseract recognition rectangle
#[derive(Debug, Copy, Clone)]
pub struct Rect {
    left: u32,
    top: u32,
    width: u32,
    height: u32,
}

impl Rect {
    /// Create new Tesseract rectangle
    pub fn new(left: u32, top: u32, width: u32, height: u32) -> Self {
        Rect {
            left,
            top,
            width,
            height,
        }
    }
}

impl From<(u32, u32, u32, u32)> for Rect {
    fn from(src: (u32, u32, u32, u32)) -> Self {
        let (left, top, width, height) = src;

        Rect {
            left,
            top,
            width,
            height,
        }
    }
}

impl From<&[u32; 4]> for Rect {
    fn from(src: &[u32; 4]) -> Self {
        let [left, top, width, height] = *src;

        Rect {
            left,
            top,
            width,
            height,
        }
    }
}

/// When Tesseract/Cube is initialized we can choose to instantiate/load/run only the Tesseract part, only the Cube part or both along with the combiner.
#[repr(u32)]
#[derive(Debug, Copy, Clone)]
pub enum EngineMode {
    /// Specify this mode when calling init_*(),
    /// to indicate that any of the above modes
    /// should be automatically inferred from the
    /// variables in the language-specific config,
    /// command-line configs, or if not specified
    /// in any of the above should be set to the
    /// default OEM_TESSERACT_ONLY.
    Default = tesseract_sys::TessOcrEngineMode_OEM_DEFAULT,
    /// Run Tesseract only - fastest; deprecated
    TesseractOnly = tesseract_sys::TessOcrEngineMode_OEM_TESSERACT_ONLY,
    /// Run just the LSTM line recognizer.
    LstmOnly = tesseract_sys::TessOcrEngineMode_OEM_LSTM_ONLY,
    /// Run the LSTM recognizer, but allow fallback
    /// to Tesseract when things get difficult.
    /// deprecated
    TesseractAndLstm = tesseract_sys::TessOcrEngineMode_OEM_TESSERACT_LSTM_COMBINED,
}

///Possible modes for page layout analysis.
/// These must be kept in order of decreasing amount of layout analysis to be done,
/// except for OSD_ONLY, so that the inequality test macros below work.
#[repr(u32)]
#[derive(Debug, Copy, Clone)]
pub enum PageSegMode {
    /// Orientation and script detection only.
    OSD = tesseract_sys::TessPageSegMode_PSM_OSD_ONLY,
    /// Automatic page segmentation with orientation and script detection. (OSD)
    AutoOSD = tesseract_sys::TessPageSegMode_PSM_AUTO_OSD,
    /// Automatic page segmentation, but no OSD, or OCR.
    AutoOnly = tesseract_sys::TessPageSegMode_PSM_AUTO_ONLY,
    /// Fully automatic page segmentation, but no OSD.
    Auto = tesseract_sys::TessPageSegMode_PSM_AUTO,
    /// Assume a single column of text of variable sizes.
    SingleColumn = tesseract_sys::TessPageSegMode_PSM_SINGLE_COLUMN,
    /// Assume a single uniform block of vertically aligned text.
    SingleBlockVertText = tesseract_sys::TessPageSegMode_PSM_SINGLE_BLOCK_VERT_TEXT,
    /// Assume a single uniform block of text. (Default.)
    SingleBlock = tesseract_sys::TessPageSegMode_PSM_SINGLE_BLOCK,
    /// Treat the image as a single text line.
    SingleLine = tesseract_sys::TessPageSegMode_PSM_SINGLE_LINE,
    /// Treat the image as a single word.
    SingleWord = tesseract_sys::TessPageSegMode_PSM_SINGLE_WORD,
    /// Treat the image as a single word in a circle.
    CircleWord = tesseract_sys::TessPageSegMode_PSM_CIRCLE_WORD,
    /// Treat the image as a single character.
    SingleChar = tesseract_sys::TessPageSegMode_PSM_SINGLE_CHAR,
    /// Find as much text as possible in no particular order.
    SparseText = tesseract_sys::TessPageSegMode_PSM_SPARSE_TEXT,
    /// Sparse text with orientation and script det.
    SparseTextOSD = tesseract_sys::TessPageSegMode_PSM_SPARSE_TEXT_OSD,
    /// Treat the image as a single text line, bypassing hacks that are Tesseract-specific.
    RawLine = tesseract_sys::TessPageSegMode_PSM_RAW_LINE,
    /// Number of enum entries.
    Count = tesseract_sys::TessPageSegMode_PSM_COUNT,
}

impl From<tesseract_sys::TessPageSegMode> for PageSegMode {
    fn from(seg_mode: tesseract_sys::TessPageSegMode) -> Self {
        match seg_mode {
            tesseract_sys::TessPageSegMode_PSM_OSD_ONLY => Self::OSD,
            tesseract_sys::TessPageSegMode_PSM_AUTO_OSD => Self::AutoOSD,
            tesseract_sys::TessPageSegMode_PSM_AUTO_ONLY => Self::AutoOnly,
            tesseract_sys::TessPageSegMode_PSM_AUTO => Self::Auto,
            tesseract_sys::TessPageSegMode_PSM_SINGLE_COLUMN => Self::SingleColumn,
            tesseract_sys::TessPageSegMode_PSM_SINGLE_BLOCK_VERT_TEXT => Self::SingleBlockVertText,
            tesseract_sys::TessPageSegMode_PSM_SINGLE_BLOCK => Self::SingleBlock,
            tesseract_sys::TessPageSegMode_PSM_SINGLE_LINE => Self::SingleLine,
            tesseract_sys::TessPageSegMode_PSM_SINGLE_WORD => Self::SingleWord,
            tesseract_sys::TessPageSegMode_PSM_CIRCLE_WORD => Self::CircleWord,
            tesseract_sys::TessPageSegMode_PSM_SINGLE_CHAR => Self::SingleChar,
            tesseract_sys::TessPageSegMode_PSM_SPARSE_TEXT => Self::SparseText,
            tesseract_sys::TessPageSegMode_PSM_SPARSE_TEXT_OSD => Self::SparseTextOSD,
            tesseract_sys::TessPageSegMode_PSM_RAW_LINE => Self::RawLine,
            tesseract_sys::TessPageSegMode_PSM_COUNT => Self::Count,
            _ => panic!("Unsupported segmentation mode: {}", seg_mode),
        }
    }
}

///enum of the elements of the page hierarchy, used in ResultIterator
///to provide functions that operate on each level without having to
///have 5x as many functions.
#[derive(Debug, Copy, Clone)]
#[repr(u32)]
pub enum PageIteratorLevel {
    /// Block of text/image/separator line.
    Block = tesseract_sys::TessPageIteratorLevel_RIL_BLOCK,
    /// Paragraph within a block.
    Para = tesseract_sys::TessPageIteratorLevel_RIL_PARA,
    /// Line within a paragraph.
    TextLine = tesseract_sys::TessPageIteratorLevel_RIL_TEXTLINE,
    ///  Word within a textline.
    Word = tesseract_sys::TessPageIteratorLevel_RIL_WORD,
    ///  Symbol/character within a word.
    Symbol = tesseract_sys::TessPageIteratorLevel_RIL_SYMBOL,
}

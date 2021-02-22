use std::convert::TryFrom;
use std::convert::TryInto;
use std::io::Read;
use std::marker::PhantomData;
use std::path::Path;

use leptonica_sys;
use leptonica_sys::l_int32;

use error::*;
use wrappers::*;

pub mod error;
mod functions;
mod helper;
mod wrappers;

pub mod prelude {
    pub use super::error::*;
    pub use super::functions::prelude::*;
    pub use super::{set_error_handler, AsLeptonicaPtr, Pix, PixData};
}

/// Set error handler
pub fn set_error_handler(handler: Option<unsafe extern "C" fn(msg: *const std::os::raw::c_char)>) {
    //TODO I should learn more is there any way to pass Rust closure as C callback pointer
    unsafe { leptonica_sys::leptSetStderrHandler(handler) };
}

pub trait AsLeptonicaPtr {
    /// Get as raw pointer
    fn as_ptr(&self) -> *mut leptonica_sys::PIX;
}

trait CheckSysValue<T> {
    // Definition in the file environ.h.
    const UNDEF: l_int32 = -1;

    /// Check is Leptonica value was defined
    fn check(self) -> Option<T>;
}

impl CheckSysValue<l_int32> for l_int32 {
    fn check(self) -> Option<l_int32> {
        if self == Self::UNDEF {
            None
        } else {
            Some(self)
        }
    }
}

#[derive(Debug)]
pub struct Pix(*mut leptonica_sys::PIX);

impl Pix {
    /// Valid Leptonica bpp (bites per pixel) (image depth)
    const VALID_BPP: &'static [u16] = &[1, 2, 4, 8, 16, 32];

    /// Check is provided bpp is valid
    fn check_bpp(depth: &u16) -> Result<()> {
        if Self::VALID_BPP.contains(depth) {
            Ok(())
        } else {
            Err(Error::new_sys_error(
                SysErrorKind::InvalidBPP,
                Some(format!("Unsupported depth: {}", depth)),
            ))
        }
    }

    /// Wrap leptonica PIX pointer
    #[track_caller]
    fn new(ptr: *mut leptonica_sys::PIX) -> Result<Self> {
        if ptr.is_null() {
            return Err(Error::new_sys_error(
                SysErrorKind::Null,
                Some("PIX pointer is null"),
            ));
        }

        Ok(Pix(ptr))
    }

    /// Create leptonica PIX with data allocated and initialized to 0
    pub fn create(width: u32, height: u32, depth: u16) -> Result<Self> {
        Self::check_bpp(&depth)?;

        unsafe { leptonica_sys::pixCreate(width as _, height as _, depth as _) }.try_into()
    }

    /// Create leptonica PIX with data allocated but not initialized
    fn create_no_init(width: u32, height: u32, depth: u16) -> Result<Self> {
        Self::check_bpp(&depth)?;

        unsafe { leptonica_sys::pixCreateNoInit(width as _, height as _, depth as _) }.try_into()
    }

    /// Create leptonica PIX with no data allocated
    fn create_header(width: u32, height: u32, depth: u16) -> Result<Self> {
        Self::check_bpp(&depth)?;

        unsafe { leptonica_sys::pixCreateHeader(width as _, height as _, depth as _) }.try_into()
    }

    /// Create leptonica PIX from provided path
    #[track_caller]
    pub fn from_path(path: impl AsRef<Path>) -> Result<Self> {
        let path = helper::path_to_cstring(path.as_ref()).expect("Can't get path as C string");

        unsafe { leptonica_sys::pixRead(path.as_ptr() as _) }.try_into()
    }

    /// Create leptonica PIX from provided memory buffer
    pub fn from_mem(src: &mut impl Read) -> Result<Self> {
        let mut buf = vec![];

        let size = src.read_to_end(&mut buf)?;

        unsafe { leptonica_sys::pixReadMem(buf.as_ptr() as _, size as _) }.try_into()
    }

    /// Trying to convert raw pixels from [`src`] to leptonica PIX
    pub fn from_raw<R: Read>(
        src: &mut R,
        width: u32,
        height: u32,
        bits_per_pixel: u16,
    ) -> Result<Self> {
        // create PIX, but do not initialise inner data
        let mut pix = Self::create_no_init(
            width,
            height,
            if bits_per_pixel == 24 {
                32
            } else {
                bits_per_pixel
            },
        )?;

        // get inner PIX data
        let mut pix_data = pix.data().expect("PIX doesn't allocate inner data");

        match bits_per_pixel {
            8 | 16 | 24 | 32 => {
                let mut index_offset: usize = 0;

                for (i, byte) in src.bytes().enumerate() {
                    pix_data.set_byte_at(i + index_offset, byte? as _);

                    // leptonica doesn't support 24 depth images...
                    // So it is a hack:
                    // * Create 32 bit PIX
                    // * Iterate over 24 bit source image
                    // * When we reach Alpha channel position just ignore it by add offset to the byte index
                    if bits_per_pixel == 24 && (i + 1) % (bits_per_pixel as usize / 8) == 0 {
                        index_offset += 1;
                    }
                }
            }
            _ => panic!(
                "Cannot convert RAW image to Pix with bpp {}",
                bits_per_pixel
            ),
        }

        //I'm not sure about it...
        pix.set_resolution(300, 300);

        Ok(pix)
    }

    #[track_caller]
    pub fn width(&self) -> u32 {
        (unsafe { leptonica_sys::pixGetWidth(self.0) })
            .check()
            .expect("Undefined width value") as _
    }

    #[track_caller]
    pub fn height(&self) -> u32 {
        (unsafe { leptonica_sys::pixGetHeight(self.0) })
            .check()
            .expect("Undefined height value") as _
    }

    #[track_caller]
    pub fn depth(&self) -> u16 {
        (unsafe { leptonica_sys::pixGetDepth(self.0) })
            .check()
            .expect("Undefined depth value") as _
    }

    /// Words per line. (How many bits per line)
    #[track_caller]
    pub fn wpl(&self) -> u32 {
        (unsafe { leptonica_sys::pixGetWpl(self.0) })
            .check()
            .expect("Undefined wpl value") as _
    }

    /// Get underlying PIX data
    pub fn data(&self) -> Option<PixData> {
        PixData::new(
            unsafe { leptonica_sys::pixGetData(self.0) },
            self.width() as usize * self.height() as usize,
            self.depth(),
        )
        .ok()
    }

    #[track_caller]
    pub fn set_resolution(&mut self, x_res: u32, y_res: u32) {
        if unsafe { leptonica_sys::pixSetResolution(self.0, x_res as _, y_res as _) } != 0 {
            panic!("Can't set PIX resolution");
        }
    }

    #[track_caller]
    pub fn set_y_res(&mut self, res: u32) {
        if unsafe { leptonica_sys::pixSetYRes(self.0, res as _) } != 0 {
            //It can be only if PIx pointer is null
            panic!("Can't set PIX Y resolution");
        }
    }

    #[track_caller]
    pub fn set_x_res(&mut self, res: u32) {
        if unsafe { leptonica_sys::pixSetXRes(self.0, res as _) } != 0 {
            //It can be only if PIx pointer is null
            panic!("Can't set PIX X resolution");
        }
    }

    #[track_caller]
    pub fn get_resolution(&self) -> (u32, u32) {
        let mut x_res = 0i32;
        let mut y_res = 0i32;

        if unsafe { leptonica_sys::pixGetResolution(self.0, &mut x_res, &mut y_res) } != 0 {
            panic!("Can't get PIX resolution");
        }

        if x_res < 0 || y_res < 0 {
            panic!("Invalid PIX resolution: X: {}, Y:{}", x_res, y_res);
        }

        (x_res as _, y_res as _)
    }

    #[track_caller]
    pub fn get_x_res(&self) -> u32 {
        let res = unsafe { leptonica_sys::pixGetXRes(self.0) };

        if res < 0 {
            panic!("Invalid PIX X resolution: {}", res);
        }

        res as _
    }

    #[track_caller]
    pub fn get_y_res(&self) -> u32 {
        let res = unsafe { leptonica_sys::pixGetYRes(self.0) };

        if res < 0 {
            panic!("Invalid PIX Y resolution: {}", res);
        }

        res as _
    }

    #[track_caller]
    pub fn write_to_file<P>(&self, path: P) -> Result<()>
    where
        P: AsRef<Path>,
    {
        let file = File::new(path, "wb+");

        //TODO I can use other formats as well...I should add cargo features to control how leptonoca was built
        let result = unsafe { leptonica_sys::pixWriteStreamBmp(file.as_ptr(), self.0) };

        if result == 0 {
            Ok(())
        } else {
            Err(Error::new_sys_error(
                SysErrorKind::NotOk,
                Some("Can't write to file"),
            ))
        }
    }
}

impl AsLeptonicaPtr for Pix {
    fn as_ptr(&self) -> *mut leptonica_sys::PIX {
        self.0
    }
}

impl TryFrom<*mut leptonica_sys::PIX> for Pix {
    type Error = Error;

    fn try_from(ptr: *mut leptonica_sys::PIX) -> std::result::Result<Self, Self::Error> {
        Self::new(ptr)
    }
}

impl Clone for Pix {
    fn clone(&self) -> Self {
        //leptonica uses its own ref manager
        unsafe { leptonica_sys::pixClone(self.0) }
            .try_into()
            .expect("Can't clone Leptonica PIX")
    }
}

impl Drop for Pix {
    fn drop(&mut self) {
        unsafe {
            leptonica_sys::pixDestroy(&mut self.0);
        }
    }
}

/// Wrapper around Leptonica data pointer
#[derive(Debug)]
pub struct PixData<'a> {
    ptr: *mut leptonica_sys::l_uint32,
    /// pixels count
    pixels: usize,
    bits_per_pixel: u16,
    pd: PhantomData<&'a ()>,
}

impl PixData<'_> {
    fn new(ptr: *mut leptonica_sys::l_uint32, pixels: usize, bits_per_pixel: u16) -> Result<Self> {
        if ptr.is_null() {
            Err(Error::new_sys_error(
                SysErrorKind::Null,
                Some("PIX data pointer is null"),
            ))
        } else {
            Ok(PixData {
                ptr,
                pixels,
                bits_per_pixel,
                pd: PhantomData,
            })
        }
    }

    /// Set byte value at specific PIX data position.
    /// [`pos`] is data array position.
    /// [`value`] is byte to set.
    pub fn set_byte_at(&mut self, pos: usize, value: u8) {
        if pos >= self.bytes_count() {
            panic!(
                "Can't set byte. Position: {} is greater than {}",
                pos,
                self.bytes_count()
            );
        }

        let ptr;
        let component;

        match self.bits_per_pixel {
            8 => {
                // we do not need to apply offset if PIX data has 1 byte depth (8 bbp)
                //so just use input params
                ptr = self.ptr;
                component = pos;
            }
            16 | 24 | 32 => {
                let bytes_per_pixel = self.bytes_per_pixel();

                // pixel offset in the array
                let offset = pos / bytes_per_pixel as usize;

                ptr = unsafe { self.ptr.add(offset) };
                //pixel component to set (R|G|B, R|G|B|A, etc...)
                component = pos - bytes_per_pixel as usize * offset;
            }
            bbp => panic!("Unsupported depth: {}", bbp),
        }

        unsafe {
            leptonica_sys::pixSetDataByteExtra(ptr, component as _, value as _);
        }
    }

    pub fn bytes_count(&self) -> usize {
        (self.pixels * self.bytes_per_pixel() as usize) as _
    }

    pub fn bytes_per_pixel(&self) -> u8 {
        (self.bits_per_pixel / 8) as _
    }
}

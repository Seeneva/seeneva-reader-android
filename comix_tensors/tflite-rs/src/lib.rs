use std::any::{Any, TypeId};
use std::convert::TryFrom;
use std::convert::TryInto;
use std::ffi::{CStr, CString};
use std::io::Read;
use std::marker::PhantomData;
use std::mem::ManuallyDrop;
use std::path::Path;

use error::*;
use pointer::{BorrowedTfLitePointer, OwnedTfLitePointer, TfLitePointer};
use tflite_sys;

pub mod error;
mod pointer;

/// Returns a string describing version information of the TensorFlow Lite library
pub fn version() -> &'static str {
    let c_str = unsafe { CStr::from_ptr(tflite_sys::TfLiteVersion()) };

    c_str.to_str().expect("Can't get TF Lite version")
}

/// Convert Tensorflow Lite status into Rust Result
fn status_into_result(status: tflite_sys::TfLiteStatus) -> Result<()> {
    match status {
        tflite_sys::TfLiteStatus_kTfLiteOk => Ok(()),
        _ => Err(Error::Sys),
    }
}

/// Base function to convert from raw pointer to object [T]
/// Return [Err] in case if pointer is null
fn from_ptr<P, T>(ptr: *mut P, f: impl FnOnce(*mut P) -> T) -> Result<T> {
    if ptr.is_null() {
        Err(Error::Sys)
    } else {
        Ok(f(ptr))
    }
}

///TensorFlow Lite Model
#[derive(Debug)]
pub struct Model<'a> {
    ptr: OwnedTfLitePointer<tflite_sys::TfLiteModel>,
    pd: PhantomData<&'a ()>,
}

impl<'a> Model<'a> {
    ///Wrap provided raw pointer
    fn from_ptr(ptr: *mut tflite_sys::TfLiteModel) -> Result<Self> {
        from_ptr(ptr, |ptr| Model {
            ptr: ptr.into(),
            pd: PhantomData,
        })
    }

    /// Create model from provided buffer
    pub fn create<'b>(buf: impl AsMut<[u8]> + 'b) -> Result<Model<'b>> {
        Self::create_inner(buf)
    }

    /// Create [Model] from [Read] source
    pub fn read(data: &mut impl Read) -> Result<Model<'static>> {
        let mut buf = ManuallyDrop::new(vec![]);

        data.read_to_end(&mut buf)?;

        let model = Self::create_inner(&mut *buf);

        if !model.is_ok() {
            //in case of error drop buffer
            ManuallyDrop::into_inner(buf);
        }

        model
    }

    fn create_inner<'b>(mut buf: impl AsMut<[u8]>) -> Result<Model<'b>> {
        let buf = buf.as_mut();

        let buf_ptr = buf.as_mut_ptr() as _;
        let buf_size = buf.len() as _;

        unsafe { tflite_sys::TfLiteModelCreate(buf_ptr, buf_size).try_into() }
    }

    /// Returns a model from the provided file
    pub fn from_path(path: impl AsRef<Path>) -> Result<Self> {
        let err_txt = || "Can't create TF Lite model from file path";

        let path = path.as_ref();

        if !path.exists() {
            return Err(Error::Path(PathErrorKind::NotExists, err_txt()));
        }

        let path_str_c = if cfg!(unix) {
            use std::os::unix::ffi::OsStrExt;
            CString::new(path.as_os_str().as_bytes())
        } else {
            CString::new(
                path.as_os_str()
                    .to_str()
                    .ok_or(Error::Path(PathErrorKind::Invalid, err_txt()))?,
            )
        }
        .map_err(|_| Error::Path(PathErrorKind::Invalid, err_txt()))?;

        unsafe { tflite_sys::TfLiteModelCreateFromFile(path_str_c.as_ptr()).try_into() }
    }
}

impl TryFrom<*mut tflite_sys::TfLiteModel> for Model<'_> {
    type Error = Error;

    fn try_from(ptr: *mut tflite_sys::TfLiteModel) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr)
    }
}

/// Tensorflow Lite delegate
#[derive(Debug)]
pub struct Delegate {
    ptr: OwnedTfLitePointer<tflite_sys::TfLiteDelegate>,
}

impl Delegate {
    ///Wrap provided raw pointer
    fn from_ptr(ptr: *mut tflite_sys::TfLiteDelegate) -> Result<Self> {
        from_ptr(ptr, |ptr| Delegate { ptr: ptr.into() })
    }

    /// This delegate encapsulates multiple GPU-acceleration APIs under the hood to
    /// make use of the fastest available on a device.
    #[cfg(feature = "with_gpu")]
    pub fn gpu() -> Result<Self> {
        unsafe {
            //use default delegate options
            tflite_sys::TfLiteGpuDelegateV2Create(std::ptr::null())
        }
        .try_into()
    }
}

impl TryFrom<*mut tflite_sys::TfLiteDelegate> for Delegate {
    type Error = Error;

    fn try_from(ptr: *mut tflite_sys::TfLiteDelegate) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr)
    }
}

/// TensorFlow Lite interpreter options
#[derive(Debug)]
pub struct InterpreterOptions {
    ptr: OwnedTfLitePointer<tflite_sys::TfLiteInterpreterOptions>,
}

impl InterpreterOptions {
    ///Wrap provided raw pointer
    fn from_ptr(ptr: *mut tflite_sys::TfLiteInterpreterOptions) -> Result<Self> {
        from_ptr(ptr, |ptr| InterpreterOptions { ptr: ptr.into() })
    }

    ///Create new interpreter options
    pub fn new() -> Self {
        unsafe { tflite_sys::TfLiteInterpreterOptionsCreate().try_into() }
            .expect("Can't create interpreter options")
    }

    /// Sets the number of CPU threads to use for the interpreter
    pub fn set_num_threads(&mut self, num_threads: i32) {
        unsafe {
            tflite_sys::TfLiteInterpreterOptionsSetNumThreads(self.ptr.as_ptr(), num_threads);
        }
    }

    /// Adds a delegate to be applied during [`TfLiteInterpreter`] creation.
    ///
    /// If delegate application fails, interpreter creation will also fail with an
    /// associated error logged.
    pub fn add_delegate(&mut self, delegate: &Delegate) {
        unsafe {
            tflite_sys::TfLiteInterpreterOptionsAddDelegate(
                self.ptr.as_ptr(),
                delegate.ptr.as_ptr(),
            );
        }
    }
}

impl TryFrom<*mut tflite_sys::TfLiteInterpreterOptions> for InterpreterOptions {
    type Error = Error;

    fn try_from(
        ptr: *mut tflite_sys::TfLiteInterpreterOptions,
    ) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr)
    }
}

///TensorFlow Lite interpreter
#[derive(Debug)]
pub struct Interpreter {
    ptr: OwnedTfLitePointer<tflite_sys::TfLiteInterpreter>,
}

unsafe impl Send for Interpreter {}

impl Interpreter {
    /// Wrap provided raw pointer
    fn from_ptr(ptr: *mut tflite_sys::TfLiteInterpreter) -> Result<Self> {
        from_ptr(ptr, |ptr| Interpreter { ptr: ptr.into() })
    }

    /// Returns a new interpreter using the provided model and options
    pub fn create(model: &Model, options: Option<&InterpreterOptions>) -> Result<Self> {
        unsafe {
            tflite_sys::TfLiteInterpreterCreate(
                model.ptr.as_ptr(),
                if let Some(options) = options {
                    options.ptr.as_ptr()
                } else {
                    std::ptr::null()
                },
            )
            .try_into()
        }
    }

    /// Returns the number of input tensors associated with the model.
    pub fn input_tensors_count(&self) -> u32 {
        (unsafe { tflite_sys::TfLiteInterpreterGetInputTensorCount(self.ptr.as_ptr()) }) as _
    }

    /// Returns the number of output tensors associated with the model.
    pub fn output_tensors_count(&self) -> u32 {
        (unsafe { tflite_sys::TfLiteInterpreterGetOutputTensorCount(self.ptr.as_ptr()) }) as _
    }

    /// Returns the tensor associated with the input index.
    /// [index] cannot be bigger than [input_tensors_count()]
    pub fn input_tensor(&self, index: u32) -> Option<Tensor> {
        if index >= self.input_tensors_count() {
            return None;
        }

        Some(
            unsafe { tflite_sys::TfLiteInterpreterGetInputTensor(self.ptr.as_ptr(), index as _) }
                .try_into()
                .expect("Can't init Tensorflow Lite input tensor"),
        )
    }

    /// Returns the tensor associated with the output [index]
    /// [index] cannot be bigger than [output_tensors_count()]
    pub fn output_tensor(&self, index: u32) -> Option<Tensor> {
        if index >= self.output_tensors_count() {
            return None;
        }

        Some(
            unsafe { tflite_sys::TfLiteInterpreterGetOutputTensor(self.ptr.as_ptr(), index as _) }
                .try_into()
                .expect("Can't init Tensorflow Lite output tensor"),
        )
    }

    /// Updates allocations for all tensors, resizing dependent tensors using the
    /// specified input tensor dimensionality.
    ///
    /// This is a relatively expensive operation, and need only be called after
    /// creating the graph and/or resizing any inputs.
    pub fn allocate_tensors(&mut self) -> Result<()> {
        let status = unsafe { tflite_sys::TfLiteInterpreterAllocateTensors(self.ptr.as_ptr()) };

        status_into_result(status)
    }

    /// Runs inference
    pub fn invoke(&mut self) -> Result<()> {
        let status = unsafe { tflite_sys::TfLiteInterpreterInvoke(self.ptr.as_ptr()) };

        status_into_result(status)
    }
}

impl TryFrom<*mut tflite_sys::TfLiteInterpreter> for Interpreter {
    type Error = Error;

    fn try_from(ptr: *mut tflite_sys::TfLiteInterpreter) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr)
    }
}

/// TensorFlow Lite Tensor
#[derive(Debug)]
pub struct Tensor<'a> {
    ptr: BorrowedTfLitePointer<'a, tflite_sys::TfLiteTensor>,
}

impl Tensor<'_> {
    ///Wrap provided raw pointer
    fn from_ptr(ptr: *mut tflite_sys::TfLiteTensor) -> Result<Self> {
        from_ptr(ptr, |ptr| Tensor { ptr: ptr.into() })
    }

    /// Returns the size of the underlying data in bytes
    pub fn size(&self) -> usize {
        (unsafe { tflite_sys::TfLiteTensorByteSize(self.ptr.as_ptr()) }) as _
    }

    /// Returns the type of a tensor element
    pub fn element_type(&self) -> TensorElementType {
        (unsafe { tflite_sys::TfLiteTensorType(self.ptr.as_ptr()) }).into()
    }

    ///Returns name of the tensor
    pub fn name(&self) -> &str {
        let c_str = unsafe { CStr::from_ptr(tflite_sys::TfLiteTensorName(self.ptr.as_ptr())) };

        c_str.to_str().expect("Can't get Tensor name")
    }

    ///Returns the number of dimensions that the tensor has
    pub fn dims_size(&self) -> usize {
        unsafe { tflite_sys::TfLiteTensorNumDims(self.ptr.as_ptr()) as _ }
    }

    /// Returns the length of the tensor
    pub fn dims(&self) -> Vec<usize> {
        (0..self.dims_size())
            .map(|index| self.dim_inner(index))
            .collect()
    }

    /// Returns the length of the tensor in the [index] dimension
    pub fn dim(&self, index: usize) -> Option<usize> {
        let size = self.dims_size();

        if index >= size {
            return None;
        }

        self.dim_inner(index).into()
    }

    /// Copies from the provided input buffer into the tensor's buffer
    /// Input buffer should have proper length. You can check Tensor size in bytes [Self::data]
    pub fn copy_from_buffer<T: Any>(&mut self, buf: impl AsRef<[T]>) -> Result<()> {
        self.check_type_inner::<T, _>(|| "Invalid input buffer type");

        let buf = buf.as_ref();

        let buf_size = std::mem::size_of_val(buf);

        if buf_size > self.size() {
            panic!(
                "Invalid input buffer size. Buffer size: {}. Tensor size: {}",
                buf_size,
                self.size()
            );
        }

        let status = unsafe {
            tflite_sys::TfLiteTensorCopyFromBuffer(
                self.ptr.as_ptr(),
                buf.as_ptr() as _,
                buf_size as _,
            )
        };

        status_into_result(status)
    }

    /// Return underlying data buffer
    ///
    /// NOTE: The result may be [None] if tensors have not yet been allocated, e.g.,
    /// if the Tensor has just been created or resized and [`Interpreter::allocate_tensors`]
    /// has yet to be called, or if the output tensor is dynamically sized and the
    /// interpreter hasn't been invoked.
    pub fn data<T: Any>(&self) -> Option<&[T]> {
        self.check_type_inner::<T, _>(|| "Invalid type");

        unsafe {
            let data_ptr = tflite_sys::TfLiteTensorData(self.ptr.as_ptr()) as *mut T;

            if data_ptr.is_null() {
                None
            } else {
                Some(std::slice::from_raw_parts_mut(
                    data_ptr,
                    self.size() / std::mem::size_of::<T>(),
                ))
            }
        }
    }

    ///Copies to the provided output buffer [byf] from the tensor's buffer
    /// Vector should have proper capacity. You can check tensor size in bytes [Self::data].
    pub fn copy_to_buffer<T: Any>(&self, buf: &mut Vec<T>) -> Result<()> {
        self.check_type_inner::<T, _>(|| "Invalid buffer type");

        let buf_size = self.size() / std::mem::size_of::<T>();

        if buf.capacity() < buf_size {
            panic!(
                "Invalid output buffer capacity. Buffer capacity: {}. Tensor size: {}",
                buf.capacity(),
                buf_size
            );
        }

        let status = unsafe {
            let res = tflite_sys::TfLiteTensorCopyToBuffer(
                self.ptr.as_ptr() as _,
                buf.as_mut_ptr() as _,
                self.size() as _,
            );

            buf.set_len(buf_size);

            res
        };

        status_into_result(status)
    }

    fn dim_inner(&self, index: usize) -> usize {
        (unsafe { tflite_sys::TfLiteTensorDim(self.ptr.as_ptr(), index as _) }) as _
    }

    fn check_type_inner<T, F>(&self, f: F)
    where
        T: Any,
        F: FnOnce() -> &'static str,
    {
        if !self.element_type().check_type::<T>() {
            panic!(
                "{}. Provided type is {:?}, Tensor elements type is {}",
                f(),
                TypeId::of::<T>(),
                self.element_type().name()
            )
        }
    }
}

impl TryFrom<*mut tflite_sys::TfLiteTensor> for Tensor<'_> {
    type Error = Error;

    fn try_from(ptr: *mut tflite_sys::TfLiteTensor) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr)
    }
}

//TODO I need to change [Tensor] api depends on *const or *mut raw pointer tflite_sys::TfLiteTensor
impl TryFrom<*const tflite_sys::TfLiteTensor> for Tensor<'_> {
    type Error = Error;

    fn try_from(ptr: *const tflite_sys::TfLiteTensor) -> std::result::Result<Self, Self::Error> {
        Self::from_ptr(ptr as *mut _)
    }
}

/// Types supported by tensor
#[repr(u32)]
#[derive(Debug, Copy, Clone)]
pub enum TensorElementType {
    NoType = tflite_sys::TfLiteType_kTfLiteNoType,
    F32 = tflite_sys::TfLiteType_kTfLiteFloat32,
    I32 = tflite_sys::TfLiteType_kTfLiteInt32,
    U8 = tflite_sys::TfLiteType_kTfLiteUInt8,
    I64 = tflite_sys::TfLiteType_kTfLiteInt64,
    String = tflite_sys::TfLiteType_kTfLiteString,
    Bool = tflite_sys::TfLiteType_kTfLiteBool,
    I16 = tflite_sys::TfLiteType_kTfLiteInt16,
    Complex64 = tflite_sys::TfLiteType_kTfLiteComplex64,
    I8 = tflite_sys::TfLiteType_kTfLiteInt8,
    F16 = tflite_sys::TfLiteType_kTfLiteFloat16,
}

impl TensorElementType {
    /// Return the name of a given type
    pub fn name(&self) -> &str {
        let c_str = unsafe { CStr::from_ptr(tflite_sys::TfLiteTypeGetName((*self) as _)) };

        c_str.to_str().expect("Can't get Tensor element name")
    }

    /// Check is [TensorElementType] equals to provided Rust type [T]
    fn check_type<T: Any>(&self) -> bool {
        TypeId::of::<T>()
            == match self {
                Self::F32 => TypeId::of::<f32>(),
                Self::I32 => TypeId::of::<i32>(),
                Self::U8 => TypeId::of::<u8>(),
                Self::I64 => TypeId::of::<i64>(),
                Self::String => TypeId::of::<&str>(),
                Self::Bool => TypeId::of::<bool>(),
                Self::I16 => TypeId::of::<i16>(),
                Self::I8 => TypeId::of::<i8>(),
                _ => return false,
            }
    }
}

impl From<tflite_sys::TfLiteType> for TensorElementType {
    fn from(tf_type: tflite_sys::TfLiteType) -> Self {
        match tf_type {
            tflite_sys::TfLiteType_kTfLiteNoType => Self::NoType,
            tflite_sys::TfLiteType_kTfLiteFloat32 => Self::F32,
            tflite_sys::TfLiteType_kTfLiteInt32 => Self::I32,
            tflite_sys::TfLiteType_kTfLiteUInt8 => Self::U8,
            tflite_sys::TfLiteType_kTfLiteInt64 => Self::I64,
            tflite_sys::TfLiteType_kTfLiteString => Self::String,
            tflite_sys::TfLiteType_kTfLiteBool => Self::Bool,
            tflite_sys::TfLiteType_kTfLiteInt16 => Self::I16,
            tflite_sys::TfLiteType_kTfLiteComplex64 => Self::Complex64,
            tflite_sys::TfLiteType_kTfLiteInt8 => Self::I8,
            tflite_sys::TfLiteType_kTfLiteFloat16 => Self::F16,
            _ => panic!("Unsupported TF Lite element type: {}", tf_type),
        }
    }
}

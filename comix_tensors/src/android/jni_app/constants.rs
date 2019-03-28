pub const COMIC_INFO_TYPE: &str = "com/almadevelop/comixreader/ComicInfo";
pub const COMIC_INFO_PAGE_TYPE: &str = "com/almadevelop/comixreader/ComicInfo$ComicInfoPage";

pub const COMIC_PAGE_DATA_TYPE: &str = "com/almadevelop/comixreader/ComicPageData";

pub const COMIC_PAGE_OBJECTS_TYPE: &str = "com/almadevelop/comixreader/ComicPageObjects";
pub const COMIC_PAGE_OBJECTS_BUILDER_TYPE: &str =
    "com/almadevelop/comixreader/ComicPageObjects$Builder";

pub mod ComicBookOpeningStates {
    pub const SUCCESS_TYPE: &str = "com/almadevelop/comixreader/Success";
    pub const CANCELLED_TYPE: &str = "com/almadevelop/comixreader/Cancelled";
    pub const CONTAINER_READ_ERROR_TYPE: &str = "com/almadevelop/comixreader/ContainerReadError";
    pub const CONTAINER_OPEN_ERROR_TYPE: &str = "com/almadevelop/comixreader/ContainerOpenError";
    pub const JNI_ERROR_TYPE: &str = "com/almadevelop/comixreader/JNIError";
    pub const CANCELLATION_ERROR_TYPE: &str = "com/almadevelop/comixreader/CancellationError";
    pub const NO_COMIC_PAGES_ERROR_TYPE: &str = "com/almadevelop/comixreader/NoComicPagesError";

    pub const CONTAINER_OPEN_ERROR_KIND_TYPE: &str =
        "com/almadevelop/comixreader/ContainerOpenError$Kind";
}

pub const JAVA_STRING_TYPE: &str = "java/lang/String";
pub const JAVA_INTEGER_TYPE: &str = "java/lang/Integer";
pub const JAVA_LONG_TYPE: &str = "java/lang/Long";
pub const JAVA_BYTE_BUFFER_TYPE: &str = "java/nio/ByteBuffer";

pub const JNI_BOOLEAN_LITERAL: &str = "Z";
pub const JNI_LONG_LITERAL: &str = "J";
pub const JNI_FLOAT_LITERAL: &str = "F";
pub const JNI_INT_LITERAL: &str = "I";
pub const JNI_VOID_LITERAL: &str = "V";

pub const JNI_CONSTRUCTOR_NAME: &str = "<init>";

//format!();
//
//macro_rules! ttt {
//    ($( $x:expr ),+) => {
//    format!("{}", 1)
//    };
//}

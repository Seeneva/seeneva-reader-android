pub const PACKAGE_NAME: &str = "com/almadevelop/comixreader";

pub const COMIC_RACK_METADATA_TYPE: &str =
    "com/almadevelop/comixreader/data/entity/ComicRackMetadata";
pub const COMIC_RACK_PAGE_TYPE: &str =
    "com/almadevelop/comixreader/data/entity/ComicRackPageMetadata";

pub const COMIC_BOOK_PAGE_TYPE: &str = "com/almadevelop/comixreader/data/entity/ComicBookPage";

pub const FILE_HASH_TYPE: &str = "com/almadevelop/comixreader/common/entity/FileHashData";

pub mod Errors {
    ///describes all native errors (shouldn't be catched on Java side)
    pub const FATAL_TYPE: &str = "com/almadevelop/comixreader/data/NativeFatalError";

    ///describes all native exceptions
    pub const EXCEPTION_TYPE: &str = "com/almadevelop/comixreader/data/NativeException";

    pub const JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE: &str = "java/lang/IllegalArgumentException";
}

pub mod Results {
    pub const COMICS_IMAGE_TYPE: &str = "com/almadevelop/comixreader/data/entity/ComicImage";

    pub const COMIC_BOOK_TYPE: &str = "com/almadevelop/comixreader/data/entity/ComicBook";
}

pub const TASK_TYPE: &str = "com/almadevelop/comixreader/data/source/jni/Native$Task";

pub const JAVA_INTEGER_TYPE: &str = "java/lang/Integer";
pub const JAVA_BOOLEAN_TYPE: &str = "java/lang/Boolean";
pub const JAVA_LONG_TYPE: &str = "java/lang/Long";

//pub const JNI_BOOLEAN_LITERAL: &str = "Z";
//pub const JNI_LONG_LITERAL: &str = "J";
//pub const JNI_FLOAT_LITERAL: &str = "F";
//pub const JNI_INT_LITERAL: &str = "I";
//pub const JNI_VOID_LITERAL: &str = "V";

pub const JNI_CONSTRUCTOR_NAME: &str = "<init>";

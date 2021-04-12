/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

pub const PACKAGE_NAME: &str = "app/seeneva/reader";

pub const COMIC_RACK_METADATA_TYPE: &str =
    "app/seeneva/reader/data/entity/ComicRackMetadata";
pub const COMIC_RACK_PAGE_TYPE: &str =
    "app/seeneva/reader/data/entity/ComicRackPageMetadata";

pub const COMIC_BOOK_PAGE_TYPE: &str = "app/seeneva/reader/data/entity/ComicBookPage";

pub const COMIC_BOOK_PAGE_OBJECT_TYPE: &str =
    "app/seeneva/reader/data/entity/ComicPageObject";

pub const FILE_HASH_TYPE: &str = "app/seeneva/reader/common/entity/FileHashData";

pub const INTERPRETER_TYPE: &str = "app/seeneva/reader/data/entity/ml/Interpreter";
pub const TESSERACT_TYPE: &str = "app/seeneva/reader/data/entity/ml/Tesseract";

pub mod Errors {
    ///describes all native errors (shouldn't be catched on Java side)
    pub const FATAL_TYPE: &str = "app/seeneva/reader/data/NativeFatalError";

    ///describes all native exceptions
    pub const EXCEPTION_TYPE: &str = "app/seeneva/reader/data/NativeException";

    pub const JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE: &str = "java/lang/IllegalArgumentException";
}

pub mod Results {
    pub const COMIC_PAGE_IMAGE_DATA_TYPE: &str =
        "app/seeneva/reader/data/entity/ComicPageImageData";

    pub const COMIC_BOOK_TYPE: &str = "app/seeneva/reader/data/entity/ComicBook";
}

pub const TASK_TYPE: &str = "app/seeneva/reader/data/source/jni/Native$Task";

pub const JAVA_INTEGER_TYPE: &str = "java/lang/Integer";
pub const JAVA_BOOLEAN_TYPE: &str = "java/lang/Boolean";
pub const JAVA_LONG_TYPE: &str = "java/lang/Long";

//pub const JNI_BOOLEAN_LITERAL: &str = "Z";
//pub const JNI_LONG_LITERAL: &str = "J";
//pub const JNI_FLOAT_LITERAL: &str = "F";
//pub const JNI_INT_LITERAL: &str = "I";
//pub const JNI_VOID_LITERAL: &str = "V";

pub const JNI_CONSTRUCTOR_NAME: &str = "<init>";

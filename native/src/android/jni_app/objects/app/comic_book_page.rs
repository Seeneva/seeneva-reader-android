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

use std::convert::TryInto;

use jni::errors::Result as JniResult;
use jni::objects::{JObject, JValue};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::comics::content::ComicPage;

use super::{comic_book_page_object, constants, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, String>> = Lazy::new(|| {
    (
        constants::COMIC_BOOK_PAGE_TYPE,
        format!(
            "(\
    JLjava/lang/String;\
    I\
    I\
    [L{};\
    )V",
            constants::COMIC_BOOK_PAGE_OBJECT_TYPE
        ),
    )
        .into()
});

pub type ComicPageMetadata<'a> = JObject<'a>;
pub type ComicPageMetadataArray<'a> = JObject<'a>;

///Map data from Rust implementation to it Java variant
pub fn new<'b>(env: &'b JNIEnv, comic_page: &ComicPage) -> JniResult<ComicPageMetadata<'b>> {
    CONSTRUCTOR.init(env).and_then(|constructor| {
        constructor.create(&[
            JValue::Long(
                comic_page
                    .pos
                    .try_into()
                    .expect("Can't convert page position to Long"),
            ),
            env.new_string(&comic_page.name)?.into(),
            JValue::Int(
                comic_page
                    .width
                    .try_into()
                    .expect("Can't convert page width to Int"),
            ),
            JValue::Int(
                comic_page
                    .height
                    .try_into()
                    .expect("Can't convert page height to Int"),
            ),
            env.auto_local(comic_book_page_object::new_array(env, &comic_page.b_boxes)?)
                .as_obj()
                .into(),
        ])
    })
}

///Create array from provided slice [`comic_pages`]
pub fn new_array<'b>(
    env: &'b JNIEnv,
    comic_pages: &[ComicPage],
) -> JniResult<ComicPageMetadataArray<'b>> {
    env.with_local_frame(comic_pages.len() as _, || {
        let array = env.new_object_array(
            comic_pages.len() as _,
            CONSTRUCTOR.init(env)?.class_obj(),
            JObject::null(),
        )?;

        for (pos, page_metadata) in comic_pages.iter().enumerate() {
            let page_metadata = new(env, page_metadata)?;
            env.set_object_array_element(array, pos as _, page_metadata)?;
        }

        Ok(JObject::from(array))
    })
}

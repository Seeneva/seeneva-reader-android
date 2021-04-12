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

use jni::errors::Result as JniResult;
use jni::objects::{JObject, JString};
use jni::sys::{jint, jlong};
use jni::JNIEnv;
use once_cell::sync::Lazy;

pub use success::new as new_success;

use crate::comics::content::ComicInfo;

use super::{comic_book_page, comic_rack, constants, JniClassConstructorCache};

mod success {
    use std::convert::TryInto;

    use crate::comics::content::ComicPage;

    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, String>> = Lazy::new(|| {
        (
            constants::Results::COMIC_BOOK_TYPE,
            format!(
                "(\
                Ljava/lang/String;\
                J[B\
                Ljava/lang/String;\
                JI\
                L{};\
                [L{};\
                )V",
                constants::COMIC_RACK_METADATA_TYPE,
                constants::COMIC_BOOK_PAGE_TYPE
            ),
        )
            .into()
    });

    pub type ComicBookSuccess<'a> = JObject<'a>;

    ///Init success Java variant
    pub fn new<'a>(
        env: &'a JNIEnv,
        path: JString,
        name: JString,
        comic_direction: jint,
        container_size: u64,
        container_hash: &[u8],
        comic_cover_position: usize,
        pages: &[ComicPage],
        info: Option<&ComicInfo>,
    ) -> JniResult<ComicBookSuccess<'a>> {
        env.with_local_frame(5, || {
            let comic_size: jlong = container_size.try_into().expect("Can't convert comic_size");

            let comic_hash = env.byte_array_from_slice(container_hash)?;

            let comic_cover_position: jlong = comic_cover_position
                .try_into()
                .expect("Can't convert cover_position");

            let comic_title = create_comic_title(env, info, name)?;
            let comic_rack_metadata = comic_rack::metadata::new(env, info)?;
            let comic_book_pages = comic_book_page::new_array(env, pages)?;

            CONSTRUCTOR.init(env).and_then(|constructor| {
                constructor.create(&[
                    JObject::from(path).into(),
                    comic_size.into(),
                    comic_hash.into(),
                    JObject::from(comic_title).into(),
                    comic_cover_position.into(),
                    comic_direction.into(),
                    comic_rack_metadata.into(),
                    comic_book_pages.into(),
                ])
            })
        })
    }

    ///Create comic book title fom [ComicInfo] if provided. Or return [default_title]
    fn create_comic_title<'a>(
        env: &'a JNIEnv,
        comic_info: Option<&ComicInfo>,
        default_title: JString<'a>,
    ) -> JniResult<JString<'a>> {
        comic_info
            .and_then(|comic_info| match &comic_info.series {
                Some(title) => Some(match &comic_info.number {
                    Some(number) => format!("{} #{}", title, number),
                    None => title.to_owned(),
                }),
                None => None,
            })
            .map_or(Ok(default_title), |title| env.new_string(title))
    }
}

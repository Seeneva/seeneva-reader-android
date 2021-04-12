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
use jni::objects::JObject;
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::comics::content::{ComicInfo as ComicInfoData, ComicInfoPage as ComicInfoPageData};

use super::{constants, java, string_to_jobject, JniClassConstructorCache};

pub mod metadata {
    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, String>> = Lazy::new(|| {
        (
            constants::COMIC_RACK_METADATA_TYPE,
            format!(
                "(Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/Integer;\
    Ljava/lang/Integer;\
    Ljava/lang/Integer;\
    Ljava/lang/Integer;\
    Ljava/lang/Integer;\
    Ljava/lang/Integer;\
    Ljava/lang/Integer;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/Boolean;\
    Ljava/lang/Boolean;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    Ljava/lang/String;\
    [L{};)V",
                constants::COMIC_RACK_PAGE_TYPE
            ),
        )
            .into()
    });

    pub type ComicRackMetadata<'a> = JObject<'a>;

    pub fn new<'a>(
        env: &'a JNIEnv,
        comic_info: Option<&ComicInfoData>,
    ) -> JniResult<ComicRackMetadata<'a>> {
        match comic_info {
            Some(comic_info) => env.with_local_frame(33, || {
                CONSTRUCTOR.init(env).and_then(|constructor| {
                    constructor.create(&[
                        string_to_jobject(env, comic_info.title.as_ref())?.into(),
                        string_to_jobject(env, comic_info.series.as_ref())?.into(),
                        string_to_jobject(env, comic_info.summary.as_ref())?.into(),
                        java::integer::new(env, comic_info.number.map(|n| n as _))?.into(),
                        java::integer::new(env, comic_info.count.map(|n| n as _))?.into(),
                        java::integer::new(env, comic_info.volume.map(|n| n as _))?.into(),
                        java::integer::new(env, comic_info.page_count.map(|n| n as _))?.into(),
                        java::integer::new(env, comic_info.year.map(|n| n as _))?.into(),
                        java::integer::new(env, comic_info.month.map(|n| n as _))?.into(),
                        java::integer::new(env, comic_info.day.map(|n| n as _))?.into(),
                        string_to_jobject(env, comic_info.publisher.as_ref())?.into(),
                        string_to_jobject(env, comic_info.writer.as_ref())?.into(),
                        string_to_jobject(env, comic_info.penciller.as_ref())?.into(),
                        string_to_jobject(env, comic_info.inker.as_ref())?.into(),
                        string_to_jobject(env, comic_info.colorist.as_ref())?.into(),
                        string_to_jobject(env, comic_info.letterer.as_ref())?.into(),
                        string_to_jobject(env, comic_info.cover_artist.as_ref())?.into(),
                        string_to_jobject(env, comic_info.editor.as_ref())?.into(),
                        string_to_jobject(env, comic_info.imprint.as_ref())?.into(),
                        string_to_jobject(env, comic_info.genre.as_ref())?.into(),
                        string_to_jobject(env, comic_info.format.as_ref())?.into(),
                        string_to_jobject(env, comic_info.age_rating.as_ref())?.into(),
                        string_to_jobject(env, comic_info.teams.as_ref())?.into(),
                        string_to_jobject(env, comic_info.locations.as_ref())?.into(),
                        string_to_jobject(env, comic_info.story_arc.as_ref())?.into(),
                        string_to_jobject(env, comic_info.series_group.as_ref())?.into(),
                        java::boolean::new(env, comic_info.black_and_white.map(|b| b as _))?.into(),
                        java::boolean::new(env, comic_info.manga.map(|b| b as _))?.into(),
                        string_to_jobject(env, comic_info.characters.as_ref())?.into(),
                        string_to_jobject(env, comic_info.web.as_ref())?.into(),
                        string_to_jobject(env, comic_info.notes.as_ref())?.into(),
                        string_to_jobject(env, comic_info.language_iso.as_ref())?.into(),
                        page::new_array(env, &comic_info.pages)?.into(),
                    ])
                })
            }),
            None => Ok(JObject::null()),
        }
    }
}

pub mod page {
    use super::*;

    static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> = Lazy::new(|| {
        (
            constants::COMIC_RACK_PAGE_TYPE,
            "(Ljava/lang/Integer;\
        Ljava/lang/String;\
        Ljava/lang/Long;\
        Ljava/lang/Integer;\
        Ljava/lang/Integer;)V",
        )
            .into()
    });

    pub type ComicRackPageMetadata<'a> = JObject<'a>;
    pub type ComicRackPageMetadataArray<'a> = JObject<'a>;

    pub fn new<'a>(
        env: &'a JNIEnv,
        page: &ComicInfoPageData,
    ) -> JniResult<ComicRackPageMetadata<'a>> {
        env.with_local_frame(5, || {
            CONSTRUCTOR.init(env).and_then(|constructor| {
                constructor.create(&[
                    java::integer::new(env, page.image.map(|n| n as _))?.into(),
                    string_to_jobject(env, page.image_type.as_ref())?.into(),
                    java::long::new(env, page.image_size.map(|n| n as _))?.into(),
                    java::integer::new(env, page.image_width.map(|n| n as _))?.into(),
                    java::integer::new(env, page.image_height.map(|n| n as _))?.into(),
                ])
            })
        })
    }

    pub fn new_array<'a>(
        env: &'a JNIEnv,
        pages: &Option<Vec<ComicInfoPageData>>,
    ) -> JniResult<ComicRackPageMetadataArray<'a>> {
        match pages {
            Some(pages) => env.with_local_frame(pages.len() as _, || {
                let pages_jni_array = env.new_object_array(
                    pages.len() as _,
                    CONSTRUCTOR.init(env)?.class_obj(),
                    JObject::null(),
                )?;

                for (i, page) in pages.into_iter().enumerate() {
                    let page = new(env, page)?;
                    env.set_object_array_element(pages_jni_array, i as _, page)?;
                }

                Ok(pages_jni_array.into())
            }),
            None => Ok(JObject::null()),
        }
    }
}

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

use jni::objects::{JObject, JString};
use jni::sys::jint;
use jni::JNIEnv;

use file_descriptor::FileRawFd;

use crate::android::jni_app::*;
use crate::comics::container::ComicContainer;
use crate::comics::content::{get_comic_book_content, GetComicBookContentError};
use crate::utils::blake_hash;

use super::{spawn_task, SpawnTaskResult};

///Start task which open comic container by it [fd], extract files from it, filter,
/// and returns supported files data
pub fn comic_book_metadata<'a>(
    env: &'a JNIEnv,
    fd: FileRawFd,
    comics_path: JString,
    comics_name: JString,
    comics_direction: jint,
    ml_interpreter: JObject<'a>,
    task_callback: JObject<'a>,
) -> SpawnTaskResult<'a> {
    let comics_path = env.new_global_ref(comics_path)?;
    let comics_name = env.new_global_ref(comics_name)?;

    let ml_interpreter = env.new_global_ref(ml_interpreter)?;

    spawn_task(
        "comic_book_metadata",
        env,
        task_callback,
        move |task, env| {
            let comics_path = comics_path;
            let comics_name = comics_name;

            let ml_interpreter = ml_interpreter;

            let mut fd = fd;

            let (pages, info, cover_position) = {
                info!("Trying to open comic book container by file descriptor");

                let mut container = fd
                    .dup()
                    .err_into_jni_throwable(Throwable::NativeFatalError, env)
                    .and_then(|fd| {
                        super::container_result_into_throwable(ComicContainer::open_fd(fd), env)
                    })?;

                task.check()?;

                info!("Trying receive ML interpreter from JNI object");

                //get tensorflow interpreter from jni object
                let mut interpreter = unwrap_task_result!(
                    app_objects::ml::Interpreter::get_inner(env, ml_interpreter.as_obj()),
                    env
                );

                info!("Process comic book container files");

                get_comic_book_content(&task, &mut container, &mut interpreter).map_err(|err| {
                    match err {
                        GetComicBookContentError::Empty => err
                            .try_as_jni_throwable(
                                Throwable::NativeException(NativeExceptionCode::EmptyBook),
                                env,
                            )
                            .into(),
                        GetComicBookContentError::Cancelled(err) => super::TaskError::from(err),
                    }
                })?
            };

            task.check()?;

            info!("Calculate comic book container hash");

            let (size, hash) = unwrap_task_result!(blake_hash(&mut fd), env);

            task.check()?;

            info!("Map comic book container into JNI object");

            app_objects::comic_book_result::new_success(
                env,
                comics_path.as_obj().into(),
                comics_name.as_obj().into(),
                comics_direction,
                size,
                &hash,
                cover_position,
                &pages,
                info.as_ref(),
            )
            .err_into_jni_throwable(Throwable::NativeFatalError, env)
            .map_err(Into::into)
        },
    )
}

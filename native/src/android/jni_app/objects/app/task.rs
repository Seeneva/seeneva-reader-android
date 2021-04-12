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
use parking_lot::Mutex;

use super::field_pointer::{set_rust_field, take_rust_field};
use crate::task::TaskHandler;

use super::{constants, JniClassConstructorCache};

static CONSTRUCTOR: Lazy<JniClassConstructorCache<&str, &str>> =
    Lazy::new(|| (constants::TASK_TYPE, "()V").into());

pub type Task<'a> = JObject<'a>;

const TASK_PTR_FIELD: &str = "id";

/// wrap [TaskHandler] into JVM Task Class
pub fn new<'a>(env: &'a JNIEnv, handler: TaskHandler) -> JniResult<Task<'a>> {
    let jni_task = CONSTRUCTOR
        .init(env)
        .and_then(|constructor| constructor.create(&[]))?;

    set_rust_field(env, jni_task, TASK_PTR_FIELD, Mutex::new(handler))?;

    Ok(jni_task)
}

/// Drop wrapped [TaskHandler] to cancel bounded task
/// It will block thread until task is locked!
pub fn cancel(env: &JNIEnv, task: Task) -> JniResult<()> {
    debug!("Trying to cancel task: {:p}", task.into_inner());

    // Now, while I do not borrow inner TaskHandler it shouldn't be locked
    // But I can do it in the future. So this block can be a problem...

    take_rust_field::<_, _, Mutex<TaskHandler>, _>(env, task, TASK_PTR_FIELD).map(|_| {
        debug!("Task {:p} is cancelled", task.into_inner());

        ()
    })
}

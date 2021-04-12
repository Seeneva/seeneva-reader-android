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
use jni::objects::{JObject, JThrowable};
use jni::JNIEnv;

///Send task [result] Java object via provided [callback]
pub fn send_task_result(env: &JNIEnv, callback: JObject, result: JObject) -> JniResult<()> {
    env.call_method(
        callback,
        "taskResult",
        "(Ljava/lang/Object;)V",
        &[result.into()],
    )
    .map(|_| ())
}

///Send task [error] Java object via provided [callback]
pub fn send_task_error(env: &JNIEnv, callback: JObject, error: JThrowable) -> JniResult<()> {
    env.call_method(
        callback,
        "taskError",
        "(Ljava/lang/Throwable;)V",
        &[JObject::from(error).into()],
    )
    .map(|_| ())
}

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
use jni::JNIEnv;

use tflite_rs::{Interpreter, InterpreterOptions, Model};

use crate::android::jni_app::*;
use crate::android::ndk::assets::{AssetManager, AssetMode};
use crate::comics::ml::PageYOLOInterpreter;

use super::{spawn_task, SpawnTaskResult};

/// Task to init TF Lite Interpreter from Android Asset
pub fn init_interpreter_from_asset<'a>(
    env: &'a JNIEnv,
    asset_mgr: JObject<'a>,
    model_file_name: JString<'a>,
    task_callback: JObject<'a>,
) -> SpawnTaskResult<'a> {
    let asset_mgr = env.new_global_ref(asset_mgr)?;
    let model_file_name: String = env.get_string(model_file_name)?.into();

    spawn_task(
        "tflite_interpreter",
        env,
        task_callback,
        move |task, env| {
            info!("Init ML Interpreter");

            let mut interpreter = {
                let asset_mgr =
                    unwrap_task_result!(AssetManager::new(env, asset_mgr.as_obj()), env);

                let mut model_asset = unwrap_task_result!(
                    asset_mgr
                        .open(&model_file_name, AssetMode::Buffer)
                        .ok_or_else(|| {
                            format!(
                                "Can't find tflite model Android asset: '{}'",
                                model_file_name
                            )
                        }),
                    env
                );

                task.check()?;

                let model = unwrap_task_result!(Model::read(&mut model_asset), env);

                let mut options = InterpreterOptions::new();
                options.set_num_threads(num_cpus::get() as _);

                unwrap_task_result!(Interpreter::create(&model, Some(&options)), env)
            };

            task.check()?;

            info!("Allocate interpreter tensors");

            unwrap_task_result!(interpreter.allocate_tensors(), env);

            task.check()?;

            info!("Wrap Interpreter into JNI object");

            // wrap YOLO interpreter into JNI object
            app_objects::ml::Interpreter::new(env, PageYOLOInterpreter::new(interpreter))
                .err_into_jni_throwable(Throwable::NativeFatalError, env)
                .map_err(Into::into)
        },
    )
}



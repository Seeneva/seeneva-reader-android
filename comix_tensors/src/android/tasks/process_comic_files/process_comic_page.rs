use crate::android::jni_app::{ComicsProcessingCallback, JniCache};
use crate::{
    get_comic_pages_objects, ComicPageObjects, Config, InterpreterInput, InterpreterOutShape,
    InterpreterOutput,
};

use std::io::Cursor;
use std::sync::Arc;
use std::thread::current as current_thread;

use jni::errors::{Error as JniError, Result as JniResult};
use jni::objects::{GlobalRef, JByteBuffer, JObject};
use jni::{JNIEnv, JavaVM};

use byteorder::{NativeEndian, ReadBytesExt, WriteBytesExt};
use ndarray::prelude::*;

use tokio::prelude::*;

///Number of bytes in the f32
const FLOAT_NUMBER_OF_BYTES: usize = 4;

///Get [InterpreterInput] contains batch of comics pages
/// Send it to the TF interpreter via JNI [app_callback]
/// Get TF interpreter output via JNI
/// Parse interpreter output, predict objects at each page of the batch
/// Send Predicted objects via JNI
pub fn process_comic_page(
    vm: Arc<JavaVM>,
    jni_cache: Arc<JniCache<'static>>,
    interpreter_input: InterpreterInput,
    app_callback: GlobalRef,
    cfg: Arc<Config<'static>>,
) -> impl Future<Item = (), Error = JniError> {
    interpreter_io(
        Arc::clone(&vm),
        interpreter_input,
        cfg.interpreter_output_shape(),
        app_callback.clone(),
    )
    .map(move |interpreter_out| get_comic_pages_objects(interpreter_out, &cfg))
    .and_then(move |batched_pages_objects| {
        send_predictions(vm, jni_cache, batched_pages_objects, app_callback)
    })
}

///Send input data to the Android code base to get output from it
fn interpreter_io(
    vm: Arc<JavaVM>,
    interpreter_input: InterpreterInput,
    out_shape: InterpreterOutShape,
    app_callback: GlobalRef,
) -> impl Future<Item = InterpreterOutput, Error = JniError> {
    future::lazy(move || {
        let env = vm.attach_current_thread()?;

        debug!(
            "Trying to send interpreter batched input via JNI. Thread {:?}",
            current_thread().name()
        );

        let InterpreterInput {
            positions,
            names,
            content,
        } = interpreter_input;

        let buf: Vec<u8> = content
            .iter()
            .map(|f| {
                let mut number_as_bytes = Vec::with_capacity(FLOAT_NUMBER_OF_BYTES);
                number_as_bytes
                    .write_f32::<NativeEndian>(*f)
                    .expect("For some reason can't convert f32 to bytes");
                number_as_bytes
            })
            .flatten()
            .collect();

        let output_buf = send_interpreter_input(&env, buf, app_callback.as_obj())?;

        debug!(
            "Got interpreter output via JNI. Thread {:?}",
            current_thread().name()
        );

        //read interpreter as byte buffer and conver it tor the 3D ndarray Array
        let interpreter_output = read_interpreter_output(&env, output_buf)?;

        let content = Array3::from_shape_vec(out_shape, interpreter_output)
            .expect("Interpreter output should be Ix3 shape");

        debug!(
            "Read interpreter output as Array. Thread {:?}",
            current_thread().name()
        );

        Ok(InterpreterOutput {
            positions,
            names,
            content,
        })
    })
}

///Send batch data to the Android Interpreter and get model output ByteBuffer from it
fn send_interpreter_input<'a>(
    env: &'a JNIEnv,
    mut data: Vec<u8>,
    callback: JObject,
) -> JniResult<JByteBuffer<'a>> {
    let data = env.new_direct_byte_buffer(&mut data)?;

    let buf = callback.call_on_pages_batch_prepared(&env, data)?;

    env.delete_local_ref(data.into())?;

    Ok(buf)
}

///Read interpreter output from JNI byte buffer [data] and convert it to the vector of floats
fn read_interpreter_output(env: &JNIEnv, data: JByteBuffer) -> JniResult<Vec<f32>> {
    let buf_adr = env.get_direct_buffer_address(data)?;

    let mut float_buf = vec![0.0f32; buf_adr.len() / FLOAT_NUMBER_OF_BYTES];

    let mut buf_adr = Cursor::new(buf_adr);

    buf_adr
        .read_f32_into::<NativeEndian>(&mut float_buf)
        .expect("Should read all data if input buffer is correct");

    Ok(float_buf)
}

fn send_predictions(
    vm: Arc<JavaVM>,
    jni_cache: Arc<JniCache<'static>>,
    batched_pages_objects: Vec<ComicPageObjects>,
    app_callback: GlobalRef,
) -> impl Future<Item = (), Error = JniError> {
    future::lazy(move || {
        let env = vm.attach_current_thread()?;

        let jni_cache = &jni_cache.comic_page_object_builder;

        debug!(
            "Start sending comic pages objects via JNI. Thread {:?}",
            current_thread().name()
        );

        //Iterate over all pages in the batch
        for page in batched_pages_objects {
            let comic_page_objects = {
                //Create new page builder via JNI
                let builder =
                    jni_cache.new_jobject(&env, page.page_position as _, &page.page_name)?;

                //Iterate over all objects at the page
                for (class_id, predictions) in page.objects.into_iter() {
                    //Iterate over all specific class objects
                    for (probability, object_box) in predictions {
                        //add object to the builder via JNI
                        jni_cache.add_object(
                            &env,
                            builder,
                            class_id as _,
                            probability,
                            object_box,
                        )?;
                    }
                }

                //build that page
                jni_cache.build(&env, builder)?
            };

            //send built page via JNI
            app_callback.call_on_comic_page_objects_detected(&env, comic_page_objects)?;
        }

        debug!(
            "Sending comic pages objects finished. Thread {:?}",
            current_thread().name()
        );

        Ok(())
    })
}

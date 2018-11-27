use super::ndk;
use crate::{parse_config, Config};

use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};
use std::ops::Deref;
use std::slice;

use jni::objects::{JObject, JString};
use jni::JNIEnv;

///Wrapper around flatbuffer config. Used with JNI
#[derive(Debug, Clone)]
pub struct ModelConfigJniWrapper<'a> {
    config: Config<'a>,
    asset: *mut ndk::AAsset,
}

unsafe impl Send for ModelConfigJniWrapper<'_> {}

impl Drop for ModelConfigJniWrapper<'_> {
    fn drop(&mut self) {
        unsafe {
            ndk::AAsset_close(self.asset);
        }
    }
}

impl<'a> Deref for ModelConfigJniWrapper<'a> {
    type Target = Config<'a>;

    fn deref(&self) -> &Self::Target {
        &self.config
    }
}

impl ModelConfigJniWrapper<'_> {
    pub unsafe fn open(
        env: &JNIEnv,
        asset_mng: JObject,
        conf_file_name: JString,
    ) -> Result<Self, ConfigAssetNotFound> {
        let asset_mng =
            ndk::AAssetManager_fromJava(env.get_native_interface(), asset_mng.into_inner());

        let flatbuffer_asset = {
            let conf_file_name_ptr = env
                .get_string_utf_chars(conf_file_name)
                .expect("Can't get string pointer for confg assets file name");

            let flatbuffer_asset =
                ndk::AAssetManager_open(asset_mng, conf_file_name_ptr, ndk::AASSET_MODE_BUFFER);

            env.release_string_utf_chars(conf_file_name, conf_file_name_ptr)
                .expect("Can't release string pointer for confg assets file name");

            flatbuffer_asset
        };

        if flatbuffer_asset.is_null() {
            return Err(ConfigAssetNotFound);
        }

        let buf = {
            let buf = ndk::AAsset_getBuffer(flatbuffer_asset);
            let buf_len = ndk::AAsset_getLength(flatbuffer_asset);

            slice::from_raw_parts(buf as *const u8, buf_len)
        };

        let config = parse_config(buf);

        Ok(ModelConfigJniWrapper {
            config,
            asset: flatbuffer_asset,
        })
    }
}

#[derive(Debug)]
pub struct ConfigAssetNotFound;

impl<'a> Display for ConfigAssetNotFound {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        writeln!(f, "Can't find model config file in the Android assets")
    }
}

impl Error for ConfigAssetNotFound {}

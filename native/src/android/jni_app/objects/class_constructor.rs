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
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JValue};
use jni::JNIEnv;
use parking_lot::{MappedRwLockReadGuard, RwLock, RwLockReadGuard};

use super::{class_loader::find_class, constants};

type CacheClass = GlobalRef;
type CacheConstructor = JMethodID<'static>;

///Description data to create new instance of the Java object
/// Contains class and constructor references
#[derive(Clone)]
struct Descr(CacheClass, CacheConstructor);

unsafe impl Sync for Descr {}
unsafe impl Send for Descr {}

#[derive(Clone)]
enum State<C, S> {
    NotInit((C, S)),
    Init(Descr),
}

impl<C, S> State<C, S>
where
    C: std::convert::AsRef<str>,
    S: std::convert::AsRef<str>,
{
    pub fn init(&mut self, env: &JNIEnv) -> JniResult<&Descr> {
        match self {
            Self::NotInit((class, signature)) => {
                //find JVM Class and make it global
                let class = find_class(env, class)?;

                //Finds Java method in the provided class and make it static lifetime
                // Use with classes with static lifetime only!
                let constructor_id =
                    env.get_method_id(class, constants::JNI_CONSTRUCTOR_NAME, signature)?;

                let class = env.new_global_ref(class)?;

                *self = State::Init(Descr(class, constructor_id.into_inner().into()));

                self.init(env)
            }
            Self::Init(descr) => Ok(descr),
        }
    }

    pub fn description(&self) -> Option<&Descr> {
        if let Self::Init(descr) = self {
            Some(descr)
        } else {
            None
        }
    }
}

pub(super) struct JniClassConstructorCache<C, S> {
    state: RwLock<State<C, S>>,
}

impl<C, S> JniClassConstructorCache<C, S>
where
    C: std::convert::AsRef<str>,
    S: std::convert::AsRef<str>,
{
    /// Provide params to init JVM Class constructor
    /// [class_type] - Class type
    /// [signature] - Class <init> signature
    pub fn new(class_type: C, signature: S) -> Self {
        JniClassConstructorCache {
            state: RwLock::new(State::NotInit((class_type, signature))),
        }
    }

    /// Init and cache JVM constructor
    pub fn init<'a, 'jni>(&'a self, env: &'jni JNIEnv) -> JniResult<Constructor<'a, 'jni>> {
        match RwLockReadGuard::try_map(self.state.read(), |state| state.description()) {
            Ok(lock) => Ok(Constructor::new(lock, env)),
            Err(reader) => {
                drop(reader);

                if let Some(mut state_writer) = self.state.try_write() {
                    state_writer.init(env)?;
                }

                self.init(env)
            }
        }
    }
}

impl<C, S> From<(C, S)> for JniClassConstructorCache<C, S>
where
    C: std::convert::AsRef<str>,
    S: std::convert::AsRef<str>,
{
    fn from(data: (C, S)) -> Self {
        Self::new(data.0, data.1)
    }
}

pub(super) struct Constructor<'lock, 'jni> {
    lock: MappedRwLockReadGuard<'lock, Descr>,
    env: &'jni JNIEnv<'jni>,
}

impl<'lock, 'jni> Constructor<'lock, 'jni> {
    fn new(lock: MappedRwLockReadGuard<'lock, Descr>, env: &'jni JNIEnv<'jni>) -> Self {
        Constructor { lock, env }
    }

    /// Return constructor target Class
    pub fn class_obj(&self) -> JClass {
        self.lock.0.as_obj().into()
    }

    /// Create new JVM object using provided constructor signature and type
    pub fn create(&self, args: &[JValue]) -> JniResult<JObject<'jni>> {
        let Descr(class, constructor) = &*self.lock;

        self.env
            .new_object_unchecked(JClass::from(class.as_obj()), *constructor, args)
    }
}

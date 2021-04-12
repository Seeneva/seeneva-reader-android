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

use std::time::Duration;

use jni::descriptors::Desc;
use jni::errors::Result as JniResult;
use jni::objects::{JFieldID, JObject, JValue};
use jni::signature::{JavaType, Primitive};
use jni::strings::JNIString;
use jni::{JNIEnv, MonitorGuard};
use parking_lot::{
    Mutex, MutexGuard, RwLock, RwLockReadGuard, RwLockUpgradableReadGuard, RwLockWriteGuard,
};

pub trait IntoJniLockPointer<T> {
    /// How much wait for lock until give up
    const LOCK_TIMEOUT: Duration = Duration::from_secs(15);

    /// Consume and return raw pointer as JNI Long
    fn into_jni_pointer(self) -> jni::sys::jlong
    where
        Self: Sized,
    {
        let mbox = Box::new(self);
        let ptr = Box::into_raw(mbox);

        ptr as _
    }

    fn from_jni_pointer(ptr: jni::sys::jlong) -> JniResult<Box<Self>>
    where
        Self: Sized,
    {
        let ptr = ptr as *mut Self;

        if ptr.is_null() {
            Err(jni::errors::Error::NullPtr("rust value from Java").into())
        } else {
            Ok(unsafe { Box::from_raw(ptr) })
        }
    }

    fn into_inner(self) -> T;

    fn is_locked(&self) -> bool;

    fn exclusive_lock(&self);
}

impl<T> IntoJniLockPointer<T> for RwLock<T> {
    fn into_inner(self) -> T {
        self.into_inner()
    }

    fn is_locked(&self) -> bool {
        self.is_locked()
    }

    fn exclusive_lock(&self) {
        drop(
            self.try_write_for(Self::LOCK_TIMEOUT)
                .expect("Can't receive exclusive RW lock"),
        );
    }
}

impl<T> IntoJniLockPointer<T> for Mutex<T> {
    fn into_inner(self) -> T {
        self.into_inner()
    }

    fn is_locked(&self) -> bool {
        self.is_locked()
    }

    fn exclusive_lock(&self) {
        drop(
            self.try_lock_for(Self::LOCK_TIMEOUT)
                .expect("Can't receive exclusive lock"),
        );
    }
}

pub struct JniField<'jni, T> {
    /// Help to lock Java object until this struct alive
    _guard: MonitorGuard<'jni>,
    ptr: *mut T,
}

impl<'jni, T> JniField<'jni, T> {
    fn new(guard: MonitorGuard<'jni>, ptr: *mut T) -> JniResult<Self> {
        if ptr.is_null() {
            Err(jni::errors::Error::NullPtr("rust value from Java").into())
        } else {
            Ok(JniField { _guard: guard, ptr })
        }
    }
}

impl<'jni, T> JniField<'jni, Mutex<T>> {
    /// Consume struct and lock inner mutex
    pub fn lock(self) -> MutexGuard<'jni, T> {
        unsafe { (*self.ptr).try_lock_for(Mutex::<T>::LOCK_TIMEOUT) }.expect("Can't receive lock")
    }
}

impl<'jni, T> JniField<'jni, RwLock<T>> {
    /// Consume struct and block until read lock can be acquired
    pub fn read(self) -> RwLockReadGuard<'jni, T> {
        unsafe { (*self.ptr).try_read_for(RwLock::<T>::LOCK_TIMEOUT) }
            .expect("Can't receive RW read lock")
    }

    /// Consume struct and block until write lock can be acquired
    pub fn write(self) -> RwLockWriteGuard<'jni, T> {
        unsafe { (*self.ptr).try_write_for(RwLock::<T>::LOCK_TIMEOUT) }
            .expect("Can't receive RW write lock")
    }

    /// Consume struct and block until upgradable lock can be acquired
    pub fn upgradable(self) -> RwLockUpgradableReadGuard<'jni, T> {
        unsafe { (*self.ptr).try_upgradable_read_for(RwLock::<T>::LOCK_TIMEOUT) }
            .expect("Can't receive RW upgradable lock")
    }
}

#[allow(unused_variables)]
pub fn set_rust_field<'jni, O, S, T, I>(
    env: &'jni JNIEnv,
    obj: O,
    field: S,
    rust_object: T,
) -> JniResult<()>
where
    O: Into<JObject<'jni>>,
    S: AsRef<str>,
    T: IntoJniLockPointer<I> + Send + 'static,
{
    let obj = obj.into();
    let class = env.auto_local(env.get_object_class(obj)?);
    let field_id: JFieldID = (&class, &field, "J").lookup(env)?;

    // It is like using synchronize on Java object
    let _guard = env.lock_obj(obj)?;

    // Check to see if we've already set this value. If it's not null, that
    // means that we're going to leak memory if it gets overwritten.
    let field_ptr = env
        .get_field_unchecked(obj, field_id, JavaType::Primitive(Primitive::Long))
        .and_then(JValue::j)? as *mut T;

    if !field_ptr.is_null() {
        return Err(jni::errors::Error::FieldAlreadySet(
            field.as_ref().to_owned(),
        ));
    }

    env.set_field_unchecked(obj, field_id, rust_object.into_jni_pointer().into())
}

pub fn get_rust_field<'jni, O, S, T, I>(
    env: &'jni JNIEnv,
    obj: O,
    field: S,
) -> JniResult<JniField<'jni, T>>
where
    O: Into<JObject<'jni>>,
    S: Into<JNIString>,
    T: IntoJniLockPointer<I> + Send + 'static,
{
    let obj = obj.into();
    let guard = env.lock_obj(obj)?;

    let ptr = env.get_field(obj, field, "J").and_then(JValue::j)? as *mut T;

    JniField::new(guard, ptr)
}

pub fn take_rust_field<'jni, O, S, T, I>(env: &'jni JNIEnv, obj: O, field: S) -> JniResult<I>
where
    O: Into<JObject<'jni>>,
    S: AsRef<str>,
    T: IntoJniLockPointer<I> + Send + 'static,
{
    take_rust_field_inner::<_, _, T, I, _>(env, obj, field, |o| {
        o.exclusive_lock();
        Ok(())
    })
}

pub fn try_take_rust_field<'jni, O, S, T, I>(env: &'jni JNIEnv, obj: O, field: S) -> JniResult<I>
where
    O: Into<JObject<'jni>>,
    S: AsRef<str>,
    T: IntoJniLockPointer<I> + Send + 'static,
{
    take_rust_field_inner::<_, _, T, I, _>(env, obj, field, |o| {
        if o.is_locked() {
            Err(jni::errors::Error::TryLock.into())
        } else {
            Ok(())
        }
    })
}

#[allow(unused_variables)]
fn take_rust_field_inner<'jni, O, S, T, I, F>(
    env: &'jni JNIEnv,
    obj: O,
    field: S,
    f: F,
) -> JniResult<I>
where
    O: Into<JObject<'jni>>,
    S: AsRef<str>,
    T: IntoJniLockPointer<I> + Send + 'static,
    F: FnOnce(&T) -> JniResult<()>,
{
    let obj = obj.into();
    let class = env.auto_local(env.get_object_class(obj)?);
    let field_id: JFieldID = (&class, &field, "J").lookup(env)?;

    let mbox = {
        let _guard = env.lock_obj(obj)?;

        let mbox = env
            .get_field_unchecked(obj, field_id, JavaType::Primitive(Primitive::Long))
            .and_then(JValue::j)
            .and_then(T::from_jni_pointer)?;

        // attempt to acquire the lock. This prevents us from consuming the
        // mutex if there's an outstanding lock. No one else will be able to
        // get a new one as long as we're in the guarded scope.
        f(&mbox)?;

        env.set_field_unchecked(
            obj,
            field_id,
            (::std::ptr::null_mut::<()>() as jni::sys::jlong).into(),
        )?;

        mbox
    };

    Ok(mbox.into_inner())
}

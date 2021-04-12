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

use crossbeam_channel;
use once_cell::sync::Lazy;
use parking_lot::Mutex;
use threadpool::{Builder as ThreadPoolBuilder, ThreadPool};

pub use self::error::*;

/// Task handler. Allow cancel bounded task
#[derive(Debug, Clone)]
pub struct TaskHandler(crossbeam_channel::Sender<()>);

#[derive(Debug, Clone)]
pub struct Task(crossbeam_channel::Receiver<()>);

impl Task {
    /// Is current task still active
    pub fn check(&self) -> Result<(), CancelledError> {
        match self.0.try_recv() {
            Err(crossbeam_channel::TryRecvError::Disconnected) => Err(CancelledError),
            _ => Ok(()),
        }
    }
}

/// Thread pool for native tasks
static TASKS_POOL: Lazy<Mutex<ThreadPool>> = Lazy::new(|| {
    Mutex::new(
        ThreadPoolBuilder::new()
            .thread_name("comics_native_task".to_string())
            .build(),
    )
});

/// Create a new task which will be called on a new thread.
/// Function returns [TaskHandler] which can be used for task cancellation.
pub fn new_task<F>(f: F) -> TaskHandler
where
    F: FnOnce(Task) + Send + 'static,
{
    let (s, r) = crossbeam_channel::bounded(0);

    // Previously I've used rayon's ThreadPool.
    // But sometimes it deadlocked after trying to receive exclusive lock of Mutex/RwLock
    // Maybe it is related to this issue -> https://github.com/rayon-rs/rayon/issues/592
    // Or I just do not understand something :)
    TASKS_POOL.lock().execute(|| {
        f(Task(r));
    });

    TaskHandler(s)
}

pub mod error {
    use thiserror::Error;

    /// Indicate that current task was cancelled
    #[derive(Error, Debug, Copy, Clone)]
    #[error("Task was cancelled")]
    pub struct CancelledError;
}

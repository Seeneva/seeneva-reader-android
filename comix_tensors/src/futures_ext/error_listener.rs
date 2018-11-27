use super::ErrorListener;

use futures_locks::MutexFut;
use tokio::prelude::*;

///Future which will be completed only than [lock] will have non None value
pub struct ErrorListenerFuture<E> {
    lock: ErrorListener<E>,
    state: State<E>,
}

impl<E> ErrorListenerFuture<E> {
    ///Create new listener from Mutex
    pub fn new(lock: ErrorListener<E>) -> Self {
        ErrorListenerFuture {
            lock,
            state: State::Idle,
        }
    }
}

enum State<E> {
    ///No future running
    Idle,
    ///Trying to get MutexGuard from future
    Running(MutexFut<Option<E>>),
}

impl<E> Future for ErrorListenerFuture<E> {
    type Item = <MutexFut<Option<E>> as Future>::Item;
    type Error = <MutexFut<Option<E>> as Future>::Error;

    fn poll(&mut self) -> Poll<Self::Item, Self::Error> {
        use self::State::*;

        match &mut self.state {
            Idle => self.state = Running(self.lock.lock()),
            Running(fut) => match fut.poll() {
                Ok(Async::Ready(mutex_guard)) => match &*mutex_guard {
                    Some(_) => return Ok(Async::Ready(mutex_guard)),
                    //change state than there is no error in the Future
                    None => self.state = Idle,
                },
                res => return res,
            },
        }

        Ok(Async::NotReady)
    }
}

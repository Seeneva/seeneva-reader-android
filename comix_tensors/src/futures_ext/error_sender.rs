use super::ErrorListener;

use futures_locks::MutexFut;
use tokio::prelude::*;

///Future which sends errors from inner [future] to the [sender]
/// Error in the [future] will be consumed
pub struct ErrorSenderFuture<F>
where
    F: Future,
{
    future: F,
    listener: ErrorListener<F::Error>,
    state: State<F::Error>,
}

enum State<E> {
    ///No error received yet
    Idle,
    ///Error received and future to get Mutex created
    Locking(MutexFut<Option<E>>, Option<E>),
}

impl<F> ErrorSenderFuture<F>
where
    F: Future,
{
    pub(super) fn new(future: F, listener: ErrorListener<F::Error>) -> Self {
        ErrorSenderFuture {
            future,
            listener,
            state: State::Idle,
        }
    }
}

impl<F> Future for ErrorSenderFuture<F>
where
    F: Future,
{
    type Item = F::Item;
    type Error = ();

    fn poll(&mut self) -> Poll<Self::Item, Self::Error> {
        use self::State::*;

        match &mut self.state {
            Idle => {
                let res = self.future.poll();

                match res {
                    Err(e) => {
                        self.state = Locking(self.listener.lock(), Some(e));
                        task::current().notify();
                        Ok(Async::NotReady)
                    }
                    _ => res.map_err(|_| ()),
                }
            }
            Locking(_, None) => {
                panic!("Wrong usage of the 'Locking' state. It can't be without error");
            }
            Locking(mutext_fut, error) => match &mut mutext_fut.poll() {
                Ok(Async::Ready(mutex)) => {
                    //send error only if there is no error set
                    if let None = &*mutex as &Option<_> {
                        mutex.replace(error.take().expect("Locking state should contain error"));
                    }

                    Err(())
                }
                Ok(Async::NotReady) => {
                    task::current().notify();
                    Ok(Async::NotReady)
                }
                Err(_) => unreachable!(),
            },
        }
    }
}

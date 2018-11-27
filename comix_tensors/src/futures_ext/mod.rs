pub mod cancel;
pub mod error_listener;
pub mod error_sender;

use self::cancel::*;
use self::error_sender::*;

use self::future::{SharedError, SharedItem};

use futures_locks::Mutex;
use tokio::prelude::*;

pub type ErrorListener<E> = Mutex<Option<E>>;

pub trait InnerFutureExt {
    ///Consume future and cancel it than [cancel_receiver] send signal to do it.
    /// Return [Cancelled] error in case of cancel event.
    fn cancellable<R, FE, RE>(self, cancel_receiver: R) -> CancelFuture<Self, R>
    where
        Self: Sized + Future<Error = FE>,
        R: Future<Item = SharedItem<CancelSignal>, Error = SharedError<RE>>,
        FE: From<Cancelled> + From<R::Error>,
        RE: Into<CancelSignalError>,
    {
        CancelFuture::new(self, cancel_receiver)
    }

    ///Consume future's error and send it to the [listener]
    fn send_error(self, listener: ErrorListener<<Self as Future>::Error>) -> ErrorSenderFuture<Self>
    where
        Self: Sized + Future,
    {
        ErrorSenderFuture::new(self, listener)
    }
}

impl<T> InnerFutureExt for T where T: Future {}

pub trait InnerStreamExt {
    ///Consume stream and cancel it than [cancel_receiver] send signal to do it.
    /// Return [Cancelled] error in case of cancel event.
    fn cancellable<R, SE, RE>(self, cancel_receiver: R) -> CancelStream<Self, R>
    where
        Self: Sized + Stream<Error = SE>,
        R: Future<Item = SharedItem<CancelSignal>, Error = SharedError<RE>>,
        SE: From<Cancelled> + From<R::Error>,
        RE: Into<CancelSignalError>,
    {
        CancelStream::new(self, cancel_receiver)
    }
}

impl<T> InnerStreamExt for T where T: Stream {}

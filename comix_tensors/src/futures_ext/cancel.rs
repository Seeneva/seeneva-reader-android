use std::error::Error;
use std::fmt::{Debug, Display, Formatter, Result as FmtResult};

use self::future::{SharedError, SharedItem};
use tokio::prelude::*;
use tokio::sync::oneshot::{channel, error::RecvError, Receiver, Sender};

///Create new cancellation channel. You can use [CancelTaskBuilder] to combine different futures
pub fn cancel_channel() -> (Sender<CancelSignal>, CancelTaskBuilder) {
    let (tx, rx) = channel::<CancelSignal>();

    (tx, CancelTaskBuilder::new(rx))
}

///Use it to combine different futures as cancellation future
pub struct CancelTaskBuilder {
    cancel_receiver: Receiver<CancelSignal>,
    others: Vec<Box<dyn Future<Item = CancelSignal, Error = CancelSignalError> + Send>>,
}

impl CancelTaskBuilder {
    fn new(cancel_receiver: Receiver<CancelSignal>) -> Self {
        CancelTaskBuilder {
            cancel_receiver,
            others: vec![],
        }
    }

    ///Add additional future which will be used as cancellation future
    pub fn additional_cancel<F, E>(&mut self, future: F) -> &mut Self
    where
        F: Future<Error = E> + Send + 'static,
        E: Debug + Send + Sync + 'static,
    {
        let future = future
            .map(|_| CancelSignal)
            .map_err(|e| CancelSignalError::from_debug(e));

        self.others.push(Box::new(future));

        self
    }

    ///Consume and build final cancellation future
    pub fn build(mut self) -> impl Future<Item = CancelSignal, Error = CancelSignalError> {
        self.others.push(Box::new(self.cancel_receiver.from_err()));

        future::select_all(self.others)
            .map(|r| r.0)
            .map_err(|e| e.0)
    }
}

///Signal to cancel task
#[derive(Debug, Copy, Clone)]
pub struct CancelSignal;

///Errors occurred during cancelation
#[derive(Debug)]
pub enum CancelSignalError {
    SenderDropped,
    Other(Box<Debug + Send + Sync>),
}

impl CancelSignalError {
    fn from_debug<T>(input: T) -> Self
    where
        T: Debug + Send + Sync + 'static,
    {
        CancelSignalError::Other(Box::new(input))
    }
}

impl From<RecvError> for CancelSignalError {
    fn from(_: RecvError) -> Self {
        CancelSignalError::SenderDropped
    }
}

impl Display for CancelSignalError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        use self::CancelSignalError::*;

        match self {
            SenderDropped => writeln!(f, "Cancel Sender was dropped while receiver is still alive"),
            Other(inner) => writeln!(
                f,
                "The error occurred while trying to cancel task. Error '{:?}'",
                inner
            ),
        }
    }
}

impl Error for CancelSignalError {}

///Used for cancel inner future
/// Inner future will be canceled than signal will be received from [cancel_receiver]
/// [Cancelled] as result
pub struct CancelFuture<F, R> {
    future: F,
    cancel_receiver: R,
}

impl<F, R> CancelFuture<F, R> {
    pub(super) fn new(future: F, cancel_receiver: R) -> Self {
        CancelFuture {
            future,
            cancel_receiver,
        }
    }
}

impl<F, R, FE, RE> Future for CancelFuture<F, R>
where
    F: Future<Error = FE>,
    R: Future<Item = SharedItem<CancelSignal>, Error = SharedError<RE>>,
    FE: From<Cancelled> + From<R::Error>,
    RE: Into<CancelSignalError>,
{
    type Item = F::Item;
    type Error = F::Error;

    fn poll(&mut self) -> Poll<Self::Item, Self::Error> {
        //if signal received than return [Cancelled]
        match self.cancel_receiver.poll() {
            Ok(Async::Ready(_)) => Err(Cancelled.into()),
            Err(e) => Err(e.into()),
            _ => self.future.poll().map_err(Into::into),
        }
    }
}

///Used for cancel inner stream
/// Inner future will be canceled than signal will be received from [cancel_receiver]
/// [Cancelled] as result
pub struct CancelStream<S, R> {
    stream: S,
    cancel_receiver: R,
}

impl<S, R> CancelStream<S, R> {
    pub(super) fn new(stream: S, cancel_receiver: R) -> Self {
        CancelStream {
            stream,
            cancel_receiver,
        }
    }
}

impl<S, R, SE, RE> Stream for CancelStream<S, R>
where
    S: Stream<Error = SE>,
    R: Future<Item = SharedItem<CancelSignal>, Error = SharedError<RE>>,
    SE: From<Cancelled> + From<R::Error>,
    RE: Into<CancelSignalError>,
{
    type Item = S::Item;
    type Error = S::Error;

    fn poll(&mut self) -> Poll<Option<Self::Item>, Self::Error> {
        match self.cancel_receiver.poll() {
            Ok(Async::Ready(_)) => Err(Cancelled.into()),
            Err(e) => Err(e.into()),
            _ => self.stream.poll().map_err(Into::into),
        }
    }
}

///Error which returns in case of cancellation signal
#[derive(Debug, Copy, Clone)]
pub struct Cancelled;

impl Display for Cancelled {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        writeln!(f, "The task has been cancelled")
    }
}

impl Error for Cancelled {}

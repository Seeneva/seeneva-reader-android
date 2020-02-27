use std::io::Result as IoResult;

use once_cell::sync::OnceCell;
use tokio::runtime::{
    Builder as TokioRuntimeBuilder, Runtime as TokioRuntime, TaskExecutor as TokioTaskExecutor,
};

static TOKIO_RUNTIME: OnceCell<TokioRuntime> = OnceCell::new();

///Init future runtime executor. Can be used to change its settings.
/// Default executor will be created if you didn't call it.
/// Return true if executor was init.
pub fn init<F>(init_fn: F) -> IoResult<bool>
where
    F: FnOnce(&mut TokioRuntimeBuilder),
{
    if TOKIO_RUNTIME.get().is_none() {
        let mut builder = TokioRuntimeBuilder::new();

        init_fn(&mut builder);

        Ok(TOKIO_RUNTIME
            .set(builder.build()?)
            .ok()
            .map(|_| true)
            .expect("Init Tokio runtime"))
    } else {
        Ok(false)
    }
}

///Return futures executor if it can be init
pub fn executor() -> IoResult<TokioTaskExecutor> {
    get_runtime().map(|runtime| runtime.executor())
}

fn get_runtime() -> IoResult<&'static TokioRuntime> {
    TOKIO_RUNTIME.get_or_try_init(|| TokioRuntime::new())
}

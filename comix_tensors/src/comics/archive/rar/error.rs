use super::OpenResult;
use std::error::Error;
use std::fmt::{Display, Formatter, Result as FmtResult};

#[derive(Debug, Clone)]
pub struct RarError(OpenResult);

impl From<RarError> for Box<dyn Error + Send> {
    fn from(err: RarError) -> Self {
        Box::new(err)
    }
}

impl From<OpenResult> for RarError {
    fn from(open_result: OpenResult) -> Self {
        assert_eq!(open_result.is_ok(), false);
        RarError(open_result)
    }
}

impl Display for RarError {
    fn fmt(&self, f: &mut Formatter) -> FmtResult {
        writeln!(
            f,
            "RAR error occurred while processing archive. RAR error {:?}",
            self.0
        )
    }
}

impl Error for RarError {}

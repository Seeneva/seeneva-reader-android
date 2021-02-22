use std::convert::TryInto;

use super::{AsLeptonicaPtr, Edges, Pix, Result};

pub trait Edge: AsLeptonicaPtr {
    ///param[in]    pixs         8 bpp; no colormap
    ///param[in]    orientflag   L_HORIZONTAL_EDGES, L_VERTICAL_EDGES, L_ALL_EDGES
    ///return  pixd   8 bpp, edges are brighter, or NULL on error
    ///
    ///Notes:
    ///     (1) Invert pixd to see larger gradients as darker (grayscale).
    ///     (2) To generate a binary image of the edges, threshold
    ///         the result using pixThresholdToBinary().  If the high
    ///         edge values are to be fg (1), invert after running
    ///         pixThresholdToBinary().
    ///     (3) Label the pixels as follows:
    ///             1    4    7
    ///             2    5    8
    ///             3    6    9
    ///         Read the data incrementally across the image and unroll
    ///         the loop.
    ///     (4) This runs at about 45 Mpix/sec on a 3 GHz processor.
    fn sobel_edge_filter(&self, orient_flag: Edges) -> Result<Pix> {
        unsafe { leptonica_sys::pixSobelEdgeFilter(self.as_ptr(), orient_flag as _) }.try_into()
    }
}

impl<T: AsLeptonicaPtr> Edge for T {}

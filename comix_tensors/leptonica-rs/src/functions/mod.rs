use super::{AsLeptonicaPtr, error::Result, Pix};

mod adaptmap;
mod bilateral;
mod binarize;
mod colorspace;
mod convolve;
mod edge;
mod enhance;
mod graymorph;
mod grayquant;
mod morph;
mod pixconv;
mod scale1;

pub mod prelude {
    pub use super::{Choose, Color, Edges};
    pub use super::adaptmap::AdaptMap;
    pub use super::bilateral::Bilateral;
    pub use super::binarize::Binarize;
    pub use super::colorspace::ColorSpace;
    pub use super::convolve::Convolve;
    pub use super::edge::Edge;
    pub use super::enhance::Enhance;
    pub use super::graymorph::GrayMorph;
    pub use super::grayquant::GrayQuant;
    pub use super::morph::Morph;
    pub use super::pixconv::PixConv;
    pub use super::scale1::Scale1;
}

#[derive(Debug, Copy, Clone)]
#[repr(u32)]
pub enum Choose {
    Min = leptonica_sys::L_CHOOSE_MIN,
    Max = leptonica_sys::L_CHOOSE_MAX,
    MaxMinDiff = leptonica_sys::L_CHOOSE_MAXDIFF,
}

#[derive(Debug, Copy, Clone)]
#[repr(u32)]
pub enum Color {
    Red = leptonica_sys::COLOR_RED,
    Green = leptonica_sys::COLOR_GREEN,
    Blue = leptonica_sys::COLOR_BLUE,
}

#[derive(Debug, Copy, Clone)]
#[repr(u32)]
pub enum Edges {
    HorizontalEdges = leptonica_sys::L_HORIZONTAL_EDGES,
    VerticalEdges = leptonica_sys::L_VERTICAL_EDGES,
    AllEdges = leptonica_sys::L_ALL_EDGES,
}

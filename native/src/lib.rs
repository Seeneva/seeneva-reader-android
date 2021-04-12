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

#[macro_use]
extern crate log;

#[cfg(target_os = "android")]
mod android;
mod comics;
mod task;
mod utils;

#[cfg(test)]
pub mod tests {
    use std::env;
    use std::path::PathBuf;

    ///Base path to all test data
    pub fn base_test_path() -> PathBuf {
        if cfg!(target_os = "android") {
            PathBuf::from("/data/local/tmp/comics_test")
        } else {
            let mut path = env::current_dir().unwrap();
            path.push("test");
            path
        }
    }
}

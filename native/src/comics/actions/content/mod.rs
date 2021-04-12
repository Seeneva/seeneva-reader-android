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

use rayon::prelude::*;

use crate::comics::container::{ComicContainer, ComicContainerVariant};
use crate::comics::ml::PageYOLOInterpreter;
use crate::task::Task;

pub use self::error::*;
use self::kind::{ComicBookContent, ComicBookContentIter};
pub use self::kind::{ComicInfo, ComicInfoPage, ComicPage};

pub mod error;
mod kind;

/// Get comic book content from provided [container]
/// All pages will be passed through ML Interpreter to found objects on them
/// Return comic book pages, comic book info and comic book cover position (relative  to container) in case of success
/// Return error in case if comic book container doesn't have any page
/// Can be cancelled using [task]
pub fn get_comic_book_content(
    task: &Task,
    container: &mut ComicContainer,
    interpreter: &mut PageYOLOInterpreter,
) -> Result<(Vec<ComicPage>, Option<ComicInfo>, usize), GetComicBookContentError> {
    let (mut pages, info) = {
        let container_files_iter = container
            .files()
            .take_while(|_| task.check().map(|_| true).unwrap_or(false))
            .filter_map(Result::ok);

        ComicBookContentIter::new(container_files_iter, task, interpreter)
    }
    .take_while(|_| task.check().map(|_| true).unwrap_or(false))
    .filter_map(Result::ok)
    .fold((vec![], None), |(mut pages, mut info_opt), content| {
        match content {
            ComicBookContent::ComicPage(page) => {
                pages.push(page);
            }
            ComicBookContent::ComicInfo(info) => {
                info_opt.replace(info);
            }
        }

        (pages, info_opt)
    });

    task.check()?;

    if pages.is_empty() {
        return Err(GetComicBookContentError::Empty);
    }

    // Some comic book containers store not sorted pages
    // So let's sort them just in case
    pages.par_sort_unstable_by(|page_left, page_right| page_left.name.cmp(&page_right.name));

    //determine position of cover in the comic container
    let cover_position = info
        .as_ref()
        .and_then(|info| info.pages.as_ref())
        .and_then(|info_pages| info_pages.into_iter().find(|p| p.is_cover()))
        .and_then(|cover_info_page| cover_info_page.image)
        .map_or_else(
            || pages.first(),
            |cover_position| pages.get(cover_position as usize),
        )
        .map(|page| page.pos)
        .expect("Should contain at least one page");

    Ok((pages, info, cover_position))
}

use std::os::unix::io::RawFd;

use jni::objects::{JObject, JString};
use jni::JNIEnv;
use tokio::prelude::*;

use crate::android::jni_app::prelude::*;
use crate::comics::prelude::*;
use crate::FileRawFd;

use super::{error::TaskError, execute_task, InitTaskResult};

mod error;

///Start Tokio task which open comic container by it [fd], extract files from it, filter,
/// and returns supported files data
/// Return error if any goes wrong or task was cancelled using [cancel_receiver]
pub fn comic_book_metadata<'a>(
    env: &'a JNIEnv,
    fd: RawFd,
    comics_path: JString,
    comics_name: JString,
    task_callback: JObject<'a>,
) -> InitTaskResult<'a> {
    let comics_path = env.new_global_ref(comics_path.into())?;
    let comics_name = env.new_global_ref(comics_name.into())?;

    let mut fd = FileRawFd::new(fd);

    let init_fd = fd.dup()?;

    let task_future = future::lazy(move || ComicContainerVariant::init(init_fd))
        .from_err::<TaskError>()
        .map(|container| container.files().from_err())
        .flatten_stream()
        .get_comic_file_metadata()
        .fold((vec![], None), |mut acc, comic_book_metadata| {
            //collect all pages and comicInfo if present
            let (pages, comic_info) = &mut acc;

            use ComicBookMetadataType::*;

            match comic_book_metadata {
                ComicPage(comic_page_metadata) => {
                    pages.push(comic_page_metadata);
                }
                ComicInfo(new_info) => {
                    comic_info.replace(new_info);
                }
            }

            future::ok::<_, Box<dyn AsJThrowable + Send>>(acc)
        })
        .and_then(move |(pages, comic_info)| {
            //calculate comic book hash and size in bytes
            archive_hash_metadata(&mut fd)
                .map(|(archive_size, archive_hash)| (archive_size, archive_hash, pages, comic_info))
                .map_err(|e| CalcArchiveHashError::from(e).into())
        });

    execute_task(
        env,
        task_callback,
        task_future,
        move |env, (archive_size, archive_hash, mut pages, comic_info)| {
            let comics_path = comics_path;
            let comics_name = comics_name;

            //sort all pages by it position in the comic book container
            pages.sort_by(|page_1, page_2| page_1.name.cmp(&page_2.name));

            //determine position of cover in the comic container
            let cover_position = comic_info
                .as_ref()
                .and_then(|comic_info| comic_info.pages.as_ref())
                .and_then(|info_pages| info_pages.into_iter().find(|p| p.is_cover()))
                .and_then(|cover_info_page| cover_info_page.image)
                .and_then(|cover_position| pages.get(cover_position as usize))
                .map(|page| page.pos)
                .unwrap_or(pages.first().expect("Should contain at least one page").pos);

            app_objects::comic_book_result::new_success(
                &env,
                comics_path.as_obj().into(),
                comics_name.as_obj().into(),
                archive_size,
                archive_hash.as_ref(),
                cover_position,
                &pages,
                &comic_info,
            )
            .map(|result| result.into_inner())
        },
    )
}

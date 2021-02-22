#!./.venv/bin/python

import os
from pathlib import Path

from PIL import Image

dataset_path = Path.cwd() / 'yolo' / 'dataset'
dataset_wide_backup_path = dataset_path.parent / 'data_wide_backup'


def split_img(src: Image.Image, src_path: Path, src_yolo_path: Path, step: float = 0.5):
    times = int(1 / step)

    for i in range(times):
        # Path to the destination image
        dst_path = img_path.parent / f"{src_path.stem}-{i}{src_path.suffix}"

        crop = (src.width * step * i, 0, src.width * step * (i+1), src.height)

        dst_w = crop[2]-crop[1]

        src.crop(crop).save(dst_path)

        if src_yolo_path.exists():
            dst_yolo_path = src_yolo_path.parent / f"{dst_path.stem}.txt"

            with open(src_yolo_path, 'r') as src_yolo:
                objects = [line.rstrip('\n').split(' ')
                           for line in src_yolo.readlines()]

            with open(dst_yolo_path, 'w') as dst_yolo:
                for obj, cx, cy, w, h in objects:
                    w = float(w) * times
                    cx = (float(cx) - step * i) * times

                    # Convert to pixels
                    w_p = w * dst_w
                    cx_p = cx * dst_w

                    # New left and right edge of the bounding box
                    lb = cx_p - w_p * 0.5
                    rb = cx_p + w_p * 0.5

                    # Do not save this bounding box if it is outside of the destination image
                    if lb < 0 or rb > dst_w:
                        continue

                    dst_yolo.write(f"{obj} {cx} {cy} {w} {h}\n")


for img_path in dataset_path.glob('*.jpg'):
    with Image.open(img_path) as img:
        if img.width > img.height:
            img_yolo_path = dataset_path / f"{img_path.stem}.txt"

            # Calculate slit step. Some images should be splitted more than once
            split_img(img, img_path, img_yolo_path,
                      1 / (img.width // img.height + 1))

            print(f"{img_path} has been splitted")

            # Move src image and YOLO to backup folder
            dataset_wide_backup_path.mkdir(exist_ok=True)

            os.rename(img_path, dataset_wide_backup_path / img_path.name)

            if img_yolo_path.exists():
                os.rename(img_yolo_path, dataset_wide_backup_path /
                          img_yolo_path.name)

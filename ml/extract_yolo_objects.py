#!./.venv/bin/python

import argparse
from pathlib import Path

from PIL import Image


def extract_object_by_id(class_id: int, dataset: Path, target: Path):
    target = target / str(class_id)

    target.mkdir(parents=True, exist_ok=True)

    for yolo_img_path in dataset.glob('*.jpg'):
        yolo_txt_path = dataset / f"{yolo_img_path.stem}.txt"

        if not yolo_txt_path.exists():
            print(f"{yolo_img_path.name} doesn't have YOLO txt file")
            continue

        with Image.open(yolo_img_path) as img:
            img_w = img.width
            img_h = img.height

            print(f"Process: {yolo_img_path.name}")

            with yolo_txt_path.open(mode='r') as yolo_txt:
                for i, yolo_line in enumerate(yolo_txt):
                    obj_class_id, obj_xc, obj_yc, obj_w, obj_h, =  yolo_line.split(
                        ' ')

                    obj_class_id = int(obj_class_id)

                    if obj_class_id != class_id:
                        continue

                    obj_xc = img_w * float(obj_xc)
                    obj_yc = img_h * float(obj_yc)
                    obj_hw = img_w * float(obj_w) * 0.5
                    obj_hh = img_h * float(obj_h) * 0.5

                    img.crop((obj_xc - obj_hw,
                            obj_yc - obj_hh,
                            obj_xc + obj_hw,
                            obj_yc + obj_hh)).save(target.joinpath(f"{yolo_img_path.stem}_{i}.png"))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Helper to crop object images from YOLO dataset')

    parser.add_argument('--class_id',
                        required=True,
                        help='Class id to crop')
    parser.add_argument('--dataset',
                        default='./yolo/dataset/',
                        help='Path to the YOLO dataset')
    parser.add_argument('--target',
                        default='./yolo/objects/',
                        help='Path where cropped objects should be stored')

    args = parser.parse_args()

    extract_object_by_id(int(args.class_id),
                         Path(args.dataset),
                         Path(args.target))

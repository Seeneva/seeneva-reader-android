#!./.venv/bin/python

import argparse
import re
from pathlib import Path


def check(dataset: Path):
    print(f"Start checking tesseract data: {dataset}")

    # Check if all objects has related txt file
    for img_path in dataset.glob('*.png'):
        tesseract_txt_path = dataset / f"{img_path.stem}.gt.txt"

        if not tesseract_txt_path.exists():
            print(
                f"Provide Tesseract {tesseract_txt_path.name} file for object '{img_path.name}'")
            continue

        fixed_img_name = re.sub('[() ]', '_', img_path.name)

        if fixed_img_name != img_path.name:
            print(f"Rename invalid file name {img_path.name} to {fixed_img_name}")
            img_path = img_path.rename(dataset / fixed_img_name)
            tesseract_txt_path = tesseract_txt_path.rename(
                dataset / f"{img_path.stem}.gt.txt")

        # Check how many lines in each tesseract txt file
        with open(tesseract_txt_path, 'r') as txt:
            lines_count = sum(1 for _ in txt)

            if lines_count != 1:
                print(
                    f"'{tesseract_txt_path.name}' file contains wrong line numbers: {lines_count}. Should be 1!")

    print("Tesseract data checked.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Check tesseract train data')

    parser.add_argument('--dataset',
                        default='./tesseract/eng_comix-ground-truth',
                        help='Tesseract train dataset')

    args = parser.parse_args()

    check(Path(args.dataset))
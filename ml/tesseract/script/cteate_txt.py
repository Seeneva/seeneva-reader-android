#!/usr/bin/python

from pathlib import Path

for img_path in Path.cwd().glob('*.png'):
    txt_path = Path.cwd() / f"{img_path.stem}.gt.txt"
    txt_path.touch(exist_ok=True)

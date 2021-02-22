#!./.venv/bin/python

from pathlib import Path

from PIL import Image

print('Calculate comic book dataset stats\n')

dataset_path = Path.cwd() / 'yolo' / 'dataset'


def pages_glob():
    return dataset_path.glob('*.jpg')


def pages_average_size():
    count = 0

    w_sum = 0
    h_sum = 0

    for img_path in pages_glob():
        count += 1

        with Image.open(img_path) as img:
            w_sum += img.width
            h_sum += img.height

    return (round(w_sum / count), round(h_sum / count))


classes = {}

with open(dataset_path.joinpath('classes.txt'), 'r') as txt:
    for i, line in enumerate(txt.readlines()):
        classes[i] = line.replace('\n', '')

if len(classes) == 0:
    raise RuntimeError('Provide YOLO classes.txt')

pages_count = len(list(pages_glob()))

print(f"Total pages count: {pages_count}")

#Paths to YOLO data .txt files
pages_yolo_data_paths = list(
    filter(lambda file: file.name != 'classes.txt' and file.stat().st_size != 0, dataset_path.glob('*.txt')))

print(f"Pages with YOLO data count: {len(pages_yolo_data_paths)}")

avr_w, avr_h = pages_average_size()

print(f"Pages average size: ({avr_w}, {avr_h})")
print(f"Pages average aspect ratio: {avr_w/avr_h}")

obj_count = {}

for page_yolo_path in pages_yolo_data_paths:
    with open(page_yolo_path, 'r') as page_yolo_data:
        for obj_line in page_yolo_data.readlines():
            yolo_obj = int(obj_line.split(' ', 1)[0])

            obj_count[classes[yolo_obj]] = obj_count.get(classes[yolo_obj], 0) + 1

print('\nObjects:')

for obj, count in obj_count.items():
    print(f"'{obj}' count: {count}")

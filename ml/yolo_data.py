#!./.venv/bin/python

from pathlib import Path
from shutil import copy

from sklearn.model_selection import train_test_split

dataset_path = Path.cwd() / 'yolo' / 'dataset'

# Check if all images has YOLO txt files even if they are empty
for img_path in dataset_path.glob('*.jpg'):
    yolo_txt_path = dataset_path / f"{img_path.stem}.txt"
    if not yolo_txt_path.exists():
        print(f"Create empty YOLO txt file: {yolo_txt_path.name}")
        yolo_txt_path.touch()

train_file_path = dataset_path.parent / 'train.txt'
test_file_path = dataset_path.parent / 'test.txt'

obj_names_path = dataset_path.parent / 'obj.names'
obj_data_path = dataset_path.parent / 'obj.data'

def write_img_paths(file_path: Path, imgs):
    with open(file_path, 'w') as f:
        for img_path in imgs:
            f.write(f"{img_path.relative_to(Path.cwd())}\n")


train, test = train_test_split(
    list(dataset_path.glob('*.jpg')), train_size=0.85)

write_img_paths(train_file_path, train)
write_img_paths(test_file_path, test)

copy(dataset_path / 'classes.txt', obj_names_path)

with open(obj_names_path, 'r') as names_file:
    class_count = sum(1 for line in names_file)

obj_data_path.write_text(f"""classes = {class_count}
train = {train_file_path.relative_to(Path.cwd())}
valid = {test_file_path.relative_to(Path.cwd())}
names = {obj_names_path.relative_to(Path.cwd())}""")

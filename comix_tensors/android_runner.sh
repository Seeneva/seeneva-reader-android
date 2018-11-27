#!/bin/sh

adb push "$PWD/test/test_comics.cbz" "/data/local/tmp/test/test_comics.cbz"
adb push "$PWD/test/test_comics.cbr" "/data/local/tmp/test/test_comics.cbr"
test_file_name=$(basename -- "$1")
adb push "$1" "/data/local/tmp/$test_file_name"
adb shell "/data/local/tmp/$test_file_name"
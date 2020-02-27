#!/bin/sh

DIRECTORY=`dirname $0`

$DIRECTORY/adb_prepare_test_data.sh

#Cargo set path via argument which can be run on Android device
echo "Rust executable path: $1"

#send rust test file to the android device and start it
test_file_name=$(basename -- "$1")

android_test_path="/data/local/tmp/comics_test"

adb push "$1" "$android_test_path" &&
#Move files from sdcard to the tmp folder and execute provided file
adb shell "cd $android_test_path && chmod +x ./$test_file_name && ./$test_file_name"
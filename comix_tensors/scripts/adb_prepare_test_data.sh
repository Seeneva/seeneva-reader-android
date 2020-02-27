#!/bin/sh

#sdcard mounted with noexec. So files cannot be executed there
android_sdcard_test_path="/sdcard/comics_test"
android_test_path="/data/local/tmp/comics_test"

adb shell "rm -rf $android_sdcard_test_path ; rm -rf $android_test_path"
#send test comics to the android device via adb
adb push "$PWD/test" "$android_sdcard_test_path" &&
adb shell "mv $android_sdcard_test_path $android_test_path"
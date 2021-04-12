#!/bin/sh

#
# This file is part of Seeneva Android Reader
# Copyright (C) 2021 Sergei Solodovnikov
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

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
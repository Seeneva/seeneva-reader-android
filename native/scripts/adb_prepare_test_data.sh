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

#sdcard mounted with noexec. So files cannot be executed there
android_sdcard_test_path="/sdcard/comics_test"
android_test_path="/data/local/tmp/comics_test"

adb shell "rm -rf $android_sdcard_test_path ; rm -rf $android_test_path"
#send test comics to the android device via adb
adb push "$PWD/test" "$android_sdcard_test_path" &&
adb shell "mv $android_sdcard_test_path $android_test_path"
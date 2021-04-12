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

# TODO Refactor. I should pick proper Binaries and C/CXX flags. Should I generate it from Gradle?

export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="TODO"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_AR="TODO"
export CC_armv7_linux_androideabi="TODO"
export CXX_armv7_linux_androideabi="TODO"
export AR_armv7_linux_androideabi="TODO"
export CXXFLAGS_armv7_linux_androideabi="-D__ANDROID_API__=17"
export CFLAGS_armv7_linux_androideabi="-D__ANDROID_API__=17"

cargo test --no-run --lib --target=armv7-linux-androideabi --release

if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
    echo "ERROR: Set ANDROID_SDK_ROOT env variable!"
    exit 1
fi

#Get connected device abi via adb
#DEVICE_ABI="$(adb shell getprop ro.product.cpu.abi2)"
DEVICE_ABI="$(echo "$(adb shell getprop ro.product.cpu.abi2)"|tr -d '\r')"

if [[ -z "${DEVICE_ABI}" ]]; then
    echo "ERROR: Can't determine Android device ABI"
    exit 1
fi

LLDB_SERVER='lldb-server'

#Final path to the lldb-server path in the Android SDK
LLDB_SERVER_PATH="$ANDROID_SDK_ROOT/lldb/3.1/android/$DEVICE_ABI/$LLDB_SERVER"

echo "LLDB server path: $LLDB_SERVER_PATH" 

#Destination path of the lldb-server on Android device
LLDB_DEVICE_PATH="/data/local/tmp/lldb"

adb shell "mkdir -p $LLDB_DEVICE_PATH" &&
#Push lldb-server to the Android device
adb push "$LLDB_SERVER_PATH" "$LLDB_DEVICE_PATH" &&

echo "LLDB server pushed on the device"
#!/bin/sh

if [[ -z "${ANDROID_HOME}" ]]; then
    echo "ERROR: Set ANDROID_HOME env variable!"
    exit 1
fi

#Get connected device abi via adb
DEVICE_ABI="$(adb shell getprop ro.product.cpu.abi)"

if [[ -z "${DEVICE_ABI}" ]]; then
    echo "ERROR: Can't determine Android device ABI"
    exit 1
fi

LLDB_SERVER="lldb-server"

#Final path to the lldb-server path in the Android SDK
LLDB_SERVER_PATH="$ANDROID_HOME/lldb/3.1/android/$DEVICE_ABI/$LLDB_SERVER"

echo "LLDB server path: $LLDB_SERVER_PATH" 

#Destination path of the lldb-server on Android device
LLDB_DEVICE_PATH="/data/local/tmp/lldb"

adb shell "mkdir -p $LLDB_DEVICE_PATH"
#Push lldb-server to the Android device
adb push "$LLDB_SERVER_PATH" "$LLDB_DEVICE_PATH"

echo "LLDB server pushed on the device"
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

# !!! Android SDK path should be provided. VSCode and CodeLLDB extension required to run this script !!!

# It is helper script which will:
# 1. Copy lldb-server to Android device and copy it to the Application sandbox directory
# 2. Start app launch Activity $APPLICATION_LAUNCH_ACTIVITY
# 3. Start lldb-server on the device
# 4. Launch CodeLLDB (VSCode extension) which will attach to the application by id

# App application_id
APPLICATION_ID=$1
# Activity name which should be started in case if application is not already started
APPLICATION_LAUNCH_ACTIVITY=$2

# Android SDK path
SDK_PATH=$3

if [ -z "$APPLICATION_ID" ]; then
    echo "Provide Android ApplicationId to debug"
    exit 1
fi

if [ -z "$SDK_PATH" ]; then
    SDK_PATH=$ANDROID_SDK_ROOT
fi

if [ -z "$SDK_PATH" ]; then
    echo "Android SDK path is unknown"
    exit 1
fi

echo "Android SDK: $SDK_PATH"

# Get connected device abi via adb
device_abi=$(adb shell getprop ro.product.cpu.abi)

if [ -z "${device_abi}" ]; then
    echo "ERROR: Can't determine Android device ABI"
    exit 1
fi

echo "Device ABI $device_abi"

# Push lldb-server on the device
adb push --sync ${SDK_PATH}/lldb/3.1/android/${device_abi}/lldb-server /data/local/tmp

# Check is our application has lldb-server inside sandbox
if [ -z "$(adb shell run-as $APPLICATION_ID ls -R lldb/bin | grep '^lldb-server$')" ]; then
    echo "Copy lldb-server to the app directory"

    adb shell run-as $APPLICATION_ID mkdir -p ./lldb/{bin, log, tmp} &&
        adb shell run-as $APPLICATION_ID cp /data/local/tmp/lldb-server ./lldb/bin &&
        adb shell run-as $APPLICATION_ID chmod 770 ./lldb/bin/lldb-server
fi

pid=$(adb shell pidof $APPLICATION_ID)

# Was application started from the script or not
app_was_lauched=false

socket="/$APPLICATION_ID/codelldb.sock"

# Check is Android app already started or not
if [ -z "$pid" ]; then
    if [ -z "$APPLICATION_LAUNCH_ACTIVITY" ]; then
        echo "Provide Android Application Activity to launch"
        exit 1
    fi

    echo "Start $APPLICATION_ID launch Activity"
    # Where is no application process we should start a new one
    # Start application Activity in debug mode
    adb shell am start -D -n "$APPLICATION_ID/$APPLICATION_LAUNCH_ACTIVITY" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

    app_was_lauched=true

    while [ -z "$pid" ]; do
        echo 'Wait for the app process'
        sleep 0.1
        pid=$(adb shell pidof $APPLICATION_ID)
    done
fi

# App process user id
uid=$(adb shell stat -c '%U' /proc/$pid)

# Here we using run-as $ApplicationId to access app sandbox and set lldb-server listening socket
# Run in background
adb shell run-as $APPLICATION_ID ./lldb/bin/lldb-server platform --server --listen unix-abstract://${socket} &

# Get lldb-server process id by user id and command it should be killed after LLDB exit
lldb_server_pid=$(adb shell pgrep -u $uid -f $socket)

echo "ApplicationID: $APPLICATION_ID, PID: $pid, UID: $uid, lldb-server-pid: $lldb_server_pid"

# LLDB init commands
init_commands="\"platform select remote-android\",\"platform connect unix-abstract-connect://$socket\","

# Iterate over all directories which contains unstripped shared library
for f in $(find ./target -mindepth 3 -maxdepth 3 -name 'libseeneva.so' -printf '%h\n' | readlink -f $(cat)); do
    # By default Android strips debug symbols from shared libraries. We need provide them. They are located inside unstripped *.so files.

    # You can check is it stripped or not using $file libYOUR_LIBRARY.so

    # You can read LLDB settings description by $lldb help settings
    init_commands="${init_commands}\"settings append target.exec-search-paths $f\","
done

if $app_was_lauched; then
    # Forward application jdwp to any open host's port
    jdp_port=$(adb forward tcp:0 jdwp:$pid)
    # We should cinnect JDB ti the app. Without it you will see "Waiting For Debugger" dialog.
    post_run_commands="\"shell jdb -attach localhost:${jdp_port}\","
    # Close forward port
    exit_commands="\"shell adb forward --remove tcp:${jdp_port}\","
fi

# Cleanup. Add kill lldb-server we do not need it any more
exit_commands="${exit_commands}\"shell adb shell run-as $APPLICATION_ID kill $lldb_server_pid\","

# CodeLLDB attach config
# Connect to an Android device
# Attach JDB to forwarded localhost port
config="{
\"name\": \"Android app debug\",
\"request\": \"attach\",
\"sourceLanguages\": [\"rust\"],
\"pid\": $pid,
\"initCommands\": [$init_commands],
\"postRunCommands\": [$post_run_commands],
\"exitCommands\": [$exit_commands],
}"

echo "Trying to launch CodeLLDB usig configuration: ${config}"

code --open-url "vscode://vadimcn.vscode-lldb/launch/config?${config}"

exit 0

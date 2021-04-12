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

cmake_minimum_required(VERSION 3.10.2)

# Dummy CMake which will generate 'cargo_build.sh' as result
# Run 'cargo_build.sh' to call cargo build
# See '${NDK}/build/cmake/android.toolchain.cmake' for CMake arguments

option(RUST_RELEASE "Specify cargo release build type" OFF)
option(RUST_DEBUG_LOGS "Library should show debug logs" ON)

# Check '${NDK}/build/cmake/android.toolchain.cmake' for variables
# Also https://cmake.org/cmake/help/latest/manual/cmake-toolchains.7.html#id21

macro(list_to_spaced_string varName)
    string(REPLACE ";" " " ${varName} "${${varName}}")
endmacro()

if(ANDROID_ABI STREQUAL armeabi-v7a)
    set(CLANG_TRIPPLE armv7a-linux-androideabi)
    set(RUST_TARGET_TRIPPLE armv7-linux-androideabi)
else()
    set(CLANG_TRIPPLE ${CMAKE_LIBRARY_ARCHITECTURE})
    set(RUST_TARGET_TRIPPLE ${CMAKE_LIBRARY_ARCHITECTURE})
endif()

if(NOT DEFINED RUST_TARGET_TRIPPLE)
    message(FATAL_ERROR "Rust target is not defined!")
endif()

# Set Cargo build features
set(RUST_BUILD_FEATURES "tflite_with_mmap")

if(ANDROID_ABI STREQUAL arm64-v8a)
    list(APPEND RUST_BUILD_FEATURES "tflite_with_ruy")
endif()

if(RUST_DEBUG_LOGS)
    list(APPEND RUST_BUILD_FEATURES "debug_logs")
endif()

list_to_spaced_string(RUST_BUILD_FEATURES)

# Tripple used by Cargo
string(REPLACE "-" "_" CARGO_TARGET_TRIPPLE ${RUST_TARGET_TRIPPLE})
string(TOUPPER ${CARGO_TARGET_TRIPPLE} CARGO_TARGET_TRIPPLE)

# Cargo command
set(CARGO_COMMAND cargo build --target=${RUST_TARGET_TRIPPLE} --features \"${RUST_BUILD_FEATURES}\")

# C/CXX flags
set(C_FLAGS ${CMAKE_C_FLAGS} ${CMAKE_SHARED_LIBRARY_C_FLAGS})
set(CXX_FLAGS ${CMAKE_CXX_FLAGS} ${CMAKE_SHARED_LIBRARY_CXX_FLAGS})

if(RUST_RELEASE)
    set(RustBuildType release)
    list(APPEND CARGO_COMMAND --release)
    list(APPEND C_FLAGS -DNDEBUG)
    list(APPEND CXX_FLAGS -DNDEBUG)
else()
    set(RustBuildType debug)
endif()

list_to_spaced_string(CARGO_COMMAND)
list_to_spaced_string(C_FLAGS)
list_to_spaced_string(CXX_FLAGS)

# Set clang compiler with proper target equal to android min level. 64-bit support starts from Android SDK 21
set(CC "${ANDROID_TOOLCHAIN_ROOT}/bin/${CLANG_TRIPPLE}${ANDROID_NATIVE_API_LEVEL}-clang")
set(CXX "${CC}++")

function(create_script)
    set(TMP_DIR tmp)
    set(CARGO_BUILD_SCRIPT "${TMP_DIR}/cargo_build.sh")

    file(MAKE_DIRECTORY ${TMP_DIR})

    # Script will be placed into {ANDROID_MODULE}/.cxx/cmake/{ANDROID_VARIANT}/{ANDROID_ABI_NAME}/

    set(CARGO_BUILD_SCRIPT_CONTENT
"#!/bin/sh

# Set required env variables

# Set libc linker and ar
# You can also set specific flags by CXX_${abi.rustTriple} (CXX_i686-linux-android)
# https://github.com/alexcrichton/cc-rs#external-configuration-via-environment-variables

export CC=\"${CC}\"
export CXX=\"${CXX}\"
export AR=\"${CMAKE_AR}\"
export LD=\"${CMAKE_LINKER}\"
export STRIP=\"${CMAKE_STRIP}\"
export LLVM_CONFIG_PATH=\"${ANDROID_TOOLCHAIN_ROOT}/bin/llvm-config\"

# Setup cargo. It can be overrided via .cargo/config TOML file
# https://doc.rust-lang.org/cargo/reference/config.html

export CARGO_TARGET_${CARGO_TARGET_TRIPPLE}_LINKER=\"${CXX}\"
export CARGO_TARGET_${CARGO_TARGET_TRIPPLE}_AR=\"${CMAKE_AR}\"

# C/CXX flags

export CFLAGS=\"${C_FLAGS}\"
export CXXFLAGS=\"${CXX_FLAGS}\"
export LDFLAGS=\"${CMAKE_SHARED_LINKER_FLAGS}\"
# Passed to cc crate. Needed for Android. Without it cc crate will use c++_shared by default
# https://github.com/alexcrichton/cc-rs#c-support
export CXXSTDLIB=\"${ANDROID_STL}\"

# CMAKE arguments
# https://developer.android.com/ndk/guides/cmake#usage

export CMAKE_TOOLCHAIN_FILE=\"${CMAKE_TOOLCHAIN_FILE}\"
export ANDROID_ABI=\"${ANDROID_ABI}\"
export ANDROID_NATIVE_API_LEVEL=\"${ANDROID_NATIVE_API_LEVEL}\"

echo 'Run cargo build for ${ANDROID_ABI}'

cd ${CMAKE_HOME_DIRECTORY} && \\
${CARGO_COMMAND}"
    )

    if(CMAKE_LIBRARY_OUTPUT_DIRECTORY)
        set(CARGO_BUILD_SCRIPT_CONTENT
"${CARGO_BUILD_SCRIPT_CONTENT} && \\
mkdir -p ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} && \\
# Copy shared libraries into output directory
cp ./target/${RUST_TARGET_TRIPPLE}/${RustBuildType}/*.so ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
        )
    endif()

    file(WRITE ${CARGO_BUILD_SCRIPT} ${CARGO_BUILD_SCRIPT_CONTENT})

    file(
        COPY ${CARGO_BUILD_SCRIPT} 
        DESTINATION ${CMAKE_BINARY_DIR}
        NO_SOURCE_PERMISSIONS
        FILE_PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
    )


    file(REMOVE_RECURSE ${TMP_DIR})
endfunction()

create_script()
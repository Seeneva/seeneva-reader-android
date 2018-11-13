# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
include(ExternalProject)

set(EIGEN_PREFIX ${CMAKE_BINARY_DIR}/eigen)
set(FLATBUFFERS_PREFIX ${CMAKE_BINARY_DIR}/flatbuffers)

set(INSTALL_PREF ${CMAKE_BINARY_DIR}/CMakeExternals/Install)
set(eigen_INSTALL ${INSTALL_PREF}/eigen)
set(flatbuffers_INSTALL ${INSTALL_PREF}/flatbuffers)

ExternalProject_Add(eigen-project
        PREFIX ${EIGEN_PREFIX}
        GIT_REPOSITORY https://github.com/eigenteam/eigen-git-mirror.git
        GIT_TAG 3.3.5
        INSTALL_DIR ${eigen_INSTALL}
        CMAKE_ARGS -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM} -DCMAKE_INSTALL_PREFIX=${eigen_INSTALL})

ExternalProject_Add(flatbuffers-project
        PREFIX ${FLATBUFFERS_PREFIX}
        GIT_REPOSITORY https://github.com/google/flatbuffers
        GIT_TAG v1.10.0
        INSTALL_DIR ${flatbuffers_INSTALL}
        CMAKE_ARGS -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM} -DCMAKE_INSTALL_PREFIX=${flatbuffers_INSTALL})


add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/cpp/native-lib.cpp)

add_dependencies(native-lib eigen-project)
add_dependencies(native-lib flatbuffers-project)

include_directories(${eigen_INSTALL}/include/eigen3)
include_directories(${flatbuffers_INSTALL}/include)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

find_library(bitmap-lib jnigraphics)
find_library(android-lib android)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        native-lib

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib} ${bitmap-lib} ${android-lib})
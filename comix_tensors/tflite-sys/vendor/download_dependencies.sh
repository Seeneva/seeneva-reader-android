#!/bin/bash
# Copyright 2017 The TensorFlow Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================

set -e

DOWNLOADS_DIR="$(dirname "$0")/downloads"
#DOWNLOADS_DIR="$1"
BZL_FILE_PATH="$(dirname "$0")/tensorflow/tensorflow/workspace.bzl"

if [[ "${OSTYPE}" == "darwin"* ]]; then
  function sha256sum() { shasum -a 256 "$@" ; }
fi

# Ensure it is being run from repo root
if [ ! -f $BZL_FILE_PATH ]; then
  echo "Could not find ${BZL_FILE_PATH}":
  echo "Likely you are not running this from the root directory of the repository.";
  exit 1;
fi

EIGEN_URL="$(grep -o 'https.*gitlab.com/libeigen/eigen/-/archive/.*tar\.gz' "${BZL_FILE_PATH}" | grep -v mirror.tensorflow | head -n1)"
EIGEN_SHA="$(eval echo $(grep '# SHARED_EIGEN_SHA' "${BZL_FILE_PATH}" | grep -o '\".*\"'))"
GEMMLOWP_URL="$(grep -o 'https://storage.googleapis.com/mirror.tensorflow.org/github.com/google/gemmlowp/.*zip' "${BZL_FILE_PATH}" | head -n1)"
GEMMLOWP_SHA="$(eval echo $(grep '# SHARED_GEMMLOWP_SHA' "${BZL_FILE_PATH}" | grep -o '\".*\"'))"
GOOGLETEST_URL="https://github.com/google/googletest/archive/release-1.8.0.tar.gz"
GOOGLETEST_SHA="58a6f4277ca2bc8565222b3bbd58a177609e9c488e8a72649359ba51450db7d8"
ABSL_URL="$(grep -o 'https://github.com/abseil/abseil-cpp/.*tar.gz' "${BZL_FILE_PATH}" | head -n1)"
ABSL_SHA="$(eval echo $(grep '# SHARED_ABSL_SHA' "${BZL_FILE_PATH}" | grep -o '\".*\"'))"
NEON_2_SSE_URL="https://github.com/intel/ARM_NEON_2_x86_SSE/archive/master.zip"
FARMHASH_URL="https://storage.googleapis.com/mirror.tensorflow.org/github.com/google/farmhash/archive/816a4ae622e964763ca0862d9dbd19324a1eaf45.tar.gz"
FARMHASH_SHA="$(eval echo $(grep '# SHARED_FARMHASH_SHA' "${BZL_FILE_PATH}" | grep -o '\".*\"'))"
FLATBUFFERS_URL="https://storage.googleapis.com/mirror.tensorflow.org/github.com/google/flatbuffers/archive/v1.11.0.tar.gz"
FLATBUFFERS_SHA="3f4a286642094f45b1b77228656fbd7ea123964f19502f9ecfd29933fd23a50b"
FFT2D_URL="https://storage.googleapis.com/mirror.tensorflow.org/www.kurims.kyoto-u.ac.jp/~ooura/fft2d.tgz"
FP16_URL="https://github.com/Maratyszcza/FP16/archive/febbb1c163726b5db24bed55cc9dc42529068997.zip"
FFT2D_SHA="ada7e99087c4ed477bfdf11413f2ba8db8a840ba9bbf8ac94f4f3972e2a7cec9"

OPENCL_URL="https://storage.googleapis.com/mirror.tensorflow.org/github.com/KhronosGroup/OpenCL-Headers/archive/0d5f18c6e7196863bc1557a693f1509adfcee056.tar.gz"
OPENCL_SHA="03cbc1fd449399be0422cdb021400f63958ef2c5a7c099a0d8f36a705b312f53"

VULKAN_URL="https://mirror.bazel.build/github.com/KhronosGroup/Vulkan-Headers/archive/0e57fc1cfa56a203efe43e4dfb9b3c9e9b105593.tar.gz"
VULKAN_SHA="096c4bff0957e9d6777b47d01c63e99ad9cf9d57e52be688a661b2473f8e52cb"

# TODO(petewarden): Some new code in Eigen triggers a clang bug with iOS arm64,
#                   so work around it by patching the source.
replace_by_sed() {
  local regex="${1}"
  shift
  # Detect the version of sed by the return value of "--version" flag. GNU-sed
  # supports "--version" while BSD-sed doesn't.
  if ! sed --version >/dev/null 2>&1; then
    # BSD-sed.
    sed -i '' -e "${regex}" "$@"
  else
    # GNU-sed.
    sed -i -e "${regex}" "$@"
  fi
}

download_and_extract() {
  local usage="Usage: download_and_extract URL DIR [SHA256]"
  local url="${1:?${usage}}"
  local dir="${2:?${usage}}"
  local sha256="${3}"
  echo "downloading ${url}" >&2
  mkdir -p "${dir}"
  tempdir=$(mktemp -d)
  filepath="${tempdir}/$(basename ${url})"
  curl -Lo ${filepath} ${url}
  if [ -n "${sha256}" ]; then
    echo "checking sha256 of ${dir}"
    echo "${sha256}  ${filepath}" | sha256sum -c
  fi
  if [[ "${url}" == *gz ]]; then
    tar -C "${dir}" --strip-components=1 -xzf ${filepath}
  elif [[ "${url}" == *zip ]]; then
    tempdir2=$(mktemp -d)
    unzip ${filepath} -d ${tempdir2}

    # If the zip file contains nested directories, extract the files from the
    # inner directory.
    if ls ${tempdir2}/*/* 1> /dev/null 2>&1; then
      # unzip has no strip components, so unzip to a temp dir, and move the
      # files we want from the tempdir to destination.
      cp -R ${tempdir2}/*/* ${dir}/
    else
      cp -R ${tempdir2}/* ${dir}/
    fi
    rm -rf ${tempdir2}
  fi
  rm -rf ${tempdir}

  # Delete any potential BUILD files, which would interfere with Bazel builds.
  find "${dir}" -type f -name '*BUILD' -delete
}

download_and_extract "${EIGEN_URL}" "${DOWNLOADS_DIR}/eigen" "${EIGEN_SHA}"
download_and_extract "${GEMMLOWP_URL}" "${DOWNLOADS_DIR}/gemmlowp" "${GEMMLOWP_SHA}"
download_and_extract "${GOOGLETEST_URL}" "${DOWNLOADS_DIR}/googletest" "${GOOGLETEST_SHA}"
download_and_extract "${ABSL_URL}" "${DOWNLOADS_DIR}/absl" "${ABSL_SHA}"
download_and_extract "${NEON_2_SSE_URL}" "${DOWNLOADS_DIR}/neon_2_sse"
download_and_extract "${FARMHASH_URL}" "${DOWNLOADS_DIR}/farmhash" "${FARMHASH_SHA}"
download_and_extract "${FLATBUFFERS_URL}" "${DOWNLOADS_DIR}/flatbuffers" "${FLATBUFFERS_SHA}"
download_and_extract "${FFT2D_URL}" "${DOWNLOADS_DIR}/fft2d" "${FFT2D_SHA}"
download_and_extract "${FP16_URL}" "${DOWNLOADS_DIR}/fp16"
download_and_extract "${OPENCL_URL}" "${DOWNLOADS_DIR}/opencl" "${OPENCL_SHA}"

replace_by_sed 's#static uint32x4_t p4ui_CONJ_XOR = vld1q_u32( conj_XOR_DATA );#static uint32x4_t p4ui_CONJ_XOR; // = vld1q_u32( conj_XOR_DATA ); - Removed by script#' \
  "${DOWNLOADS_DIR}/eigen/Eigen/src/Core/arch/NEON/Complex.h"
replace_by_sed 's#static uint32x2_t p2ui_CONJ_XOR = vld1_u32( conj_XOR_DATA );#static uint32x2_t p2ui_CONJ_XOR;// = vld1_u32( conj_XOR_DATA ); - Removed by scripts#' \
  "${DOWNLOADS_DIR}/eigen/Eigen/src/Core/arch/NEON/Complex.h"
replace_by_sed 's#static uint64x2_t p2ul_CONJ_XOR = vld1q_u64( p2ul_conj_XOR_DATA );#static uint64x2_t p2ul_CONJ_XOR;// = vld1q_u64( p2ul_conj_XOR_DATA ); - Removed by script#' \
  "${DOWNLOADS_DIR}/eigen/Eigen/src/Core/arch/NEON/Complex.h"

echo "download_dependencies.sh completed successfully." >&2

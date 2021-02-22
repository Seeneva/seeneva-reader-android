#!/bin/sh

# Path to tesstrain https://github.com/tesseract-ocr/tesstrain
TESSTRAIN_PATH=$1

MODEL_NAME='eng_comix'
START_MODEL='eng'
MAX_ITERATIONS=20000

DATA_SRC_PATH=$(realpath ../cut)
DATA_DST_PATH="./data/$MODEL_NAME-ground-truth"

# Copy all files into tesstrain data folder
cd $TESSTRAIN_PATH && \
mkdir -p $DATA_DST_PATH && \
cp $DATA_SRC_PATH/* $DATA_DST_PATH && \
# Make all images gray
magick mogrify -colorspace gray $DATA_DST_PATH/*.png &&\
# Download language
make tesseract-langs MODEL_NAME=$MODEL_NAME START_MODEL=$START_MODEL && \
# Start training
make training MODEL_NAME=$MODEL_NAME START_MODEL=$START_MODEL PSM=13 MAX_ITERATIONS=$MAX_ITERATIONS
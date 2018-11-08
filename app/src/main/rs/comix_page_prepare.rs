#pragma version(1)
#pragma rs java_package_name(com.almadevelop.comixreader)

static uint32_t pixel_count;
static float mean;
static float std;

uint32_t RS_KERNEL calculatePixelSum(uchar4 in) {
    return in[0] + in[1] + in[2];
}

float RS_KERNEL calculatePixelStdSum(uchar4 in) {
    float sum = 0.0;

    for(uchar i = 0; i < 3; i++) {
        sum += pown((float)(in[i] - mean), 2);
    }

    return sum;
}

float3 RS_KERNEL normalizeImageVector(uchar4 in) {
    return (float3) {(in.r - mean) / std, (in.g - mean) / std, (in.b - mean) / std};
}

static float calculateImageMean(rs_allocation imageAllocation, uint32_t w, uint32_t h) {
    rs_allocation pix = rsCreateAllocation_uint(w, h);

    rsForEach(calculatePixelSum, imageAllocation, pix);

    uint32_t mean = 0;

    for(uint32_t x = 0; x < w; x++) {
        for (uint32_t y = 0; y < h; y++){
            mean += rsGetElementAt_uint(pix, x, y);
        }
    }

    rsClearObject(&pix);

    return (float) mean / pixel_count;
}

static float calculateImageSTD(rs_allocation imageAllocation, uint32_t w, uint32_t h){
    rs_allocation pix = rsCreateAllocation_float(w, h);

    rsForEach(calculatePixelStdSum, imageAllocation, pix);

    float sum = 0.0;

    for(uint32_t x = 0; x < w; x++) {
        for (uint32_t y = 0; y < h; y++) {
            sum += rsGetElementAt_float(pix, x, y);
        }
    }

    rsClearObject(&pix);

    return sqrt(sum / pixel_count);
}

void proceed(rs_allocation imageAllocation, rs_allocation normImgAllocation) {
    const uint32_t w = rsAllocationGetDimX(imageAllocation);
    const uint32_t h = rsAllocationGetDimY(imageAllocation);

    pixel_count = w * h * 3;

    //mean of the image
    mean = calculateImageMean(imageAllocation, w, h);
    std = calculateImageSTD(imageAllocation, w, h);
    rsForEach(normalizeImageVector, imageAllocation, normImgAllocation);

    rsClearObject(&imageAllocation);
}
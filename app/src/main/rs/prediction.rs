#pragma version(1)
#pragma rs java_package_name(com.almadevelop.comixreader)

//[-6.801582, -7.40066, -0.0033608116, -0.003429736, -0.0031742891, -0.0032241403, 0.0, 0.0, 0.0, 0.0]
typedef struct ModelPred {
    float y[5];
} ModelPred_t;

ModelPred_t RS_KERNEL initializeModelPreds() {
    ModelPred_t out;
    return out;
}

void decodePred(rs_allocation pred,
                uint32_t classCount,
                uint32_t batchSize,
                uint32_t anchorsHeight,
                uint32_t anchorsWidth,
                uint32_t anchorPerGrid) {
     uint32_t output = rsAllocationGetDimX(pred);
     uint8_t totalClassCount = classCount + 1 + 4;
    //ModelPred_t *t = (struct ModelPred_t *) rsGetElementAt(pred, 0);
    //rsDebug("!!!!", t->x0);

    rs_allocation s = rsCreateAllocation_float2(batchSize, anchorsHeight, anchorsWidth);

    uint32_t x = 0;
    uint32_t y = 0;
    uint32_t z = 0;

    for(uint16_t i = 0; i < totalClassCount; i++) {
        const ModelPred_t *t = (struct ModelPred_t *) rsGetElementAt(pred, i);
    }

}
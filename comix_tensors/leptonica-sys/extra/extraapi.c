#include "extraapi.h"

void pixSetDataByteExtra(l_uint32 *data, l_int32 n, l_uint8 val)
{
    SET_DATA_BYTE(data, n, val);
}

void pixSetDataBitValExtra(l_uint32 *data, l_int32 n, l_uint8 val)
{
    SET_DATA_BIT_VAL(data, n, val);
}
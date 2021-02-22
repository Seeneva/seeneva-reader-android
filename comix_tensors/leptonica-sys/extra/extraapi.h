#ifndef API_EXTRAAPI_H_
#define API_EXTRAAPI_H_

#include <leptonica/arrayaccess.h>
#include <leptonica/environ.h>

// Wrapper around C macro to help with Rust bindings
void pixSetDataByteExtra(l_uint32 *data, l_int32 n, l_uint8 val);

// Clear and set bit at the same time
void pixSetDataBitValExtra(l_uint32 *data, l_int32 n, l_uint8 val);

#endif // API_EXTRAAPI_H_
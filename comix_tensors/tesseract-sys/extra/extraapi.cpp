#ifndef TESS_CAPI_INCLUDE_BASEAPI
#  define TESS_CAPI_INCLUDE_BASEAPI
#endif

#include "extraapi.h"

TESS_API int TESS_CALL TessBaseAPIInitExtra(TessBaseAPI *handle,
                                            const char *data,
                                            int data_size,
                                            const char *language,
                                            TessOcrEngineMode oem,
                                            char **configs,
                                            int configs_size)
{
    return handle->Init(data, data_size, language, oem, configs, configs_size, nullptr,
                        nullptr, false, nullptr);
}
#ifndef API_EXTRAAPI_H_
#define API_EXTRAAPI_H_

#if defined(TESSERACT_API_BASEAPI_H_) && !defined(TESS_CAPI_INCLUDE_BASEAPI)
#  define TESS_CAPI_INCLUDE_BASEAPI
#endif

#ifdef TESS_CAPI_INCLUDE_BASEAPI
#  include <tesseract/baseapi.h>
#else
#  include <tesseract/capi.h>
#  include <stdbool.h>
#  include <stdio.h>
#  include <tesseract/platform.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

#ifndef TESS_CALL
#  if defined(WIN32)
#    define TESS_CALL __cdecl
#  else
#    define TESS_CALL
#  endif
#endif

//extern struct TessBaseAPI TessBaseAPI;
#ifdef TESS_CAPI_INCLUDE_BASEAPI
   typedef tesseract::TessBaseAPI TessBaseAPI;
   typedef tesseract::OcrEngineMode TessOcrEngineMode;
#endif

    TESS_API int TESS_CALL TessBaseAPIInitExtra(TessBaseAPI *handle,
                                                const char *data,
                                                int data_size,
                                                const char *language,
                                                TessOcrEngineMode oem,
                                                char **configs,
                                                int configs_size);

#ifdef __cplusplus
}
#endif

#endif  // API_EXTRAAPI_H_

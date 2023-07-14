#ifndef INCLUDE_UPSCALE_ENGINE_H
#define INCLUDE_UPSCALE_ENGINE_H

#include "filesystem_utils.h"

class UpscaleEngine {
public:
    UpscaleEngine();

    ~UpscaleEngine();

    static int exec(path_t inputpath, path_t outputpath);
};

#endif//INCLUDE_UPSCALE_ENGINE_H
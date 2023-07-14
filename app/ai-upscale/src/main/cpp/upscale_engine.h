#ifndef INCLUDE_UPSCALE_ENGINE_H
#define INCLUDE_UPSCALE_ENGINE_H

#include "filesystem_utils.h"

class UpscaleEngine {
public:
    UpscaleEngine();

    ~UpscaleEngine();

    void useModelAssets(AAssetManager* assetMgr, const char* param, const char* model);

    int exec(path_t inputpath, path_t outputpath);

private:
    AAssetManager *asset_manager;
    const char *param_path;
    const char *model_path;
};

#endif//INCLUDE_UPSCALE_ENGINE_H
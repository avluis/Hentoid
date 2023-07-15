#ifndef INCLUDE_UPSCALE_ENGINE_H
#define INCLUDE_UPSCALE_ENGINE_H

#include "filesystem_utils.h"

class UpscaleEngine {
public:
    UpscaleEngine();

    ~UpscaleEngine();

    void useModelAssets(AAssetManager* assetMgr, const char* param, const char* model);

    static int exec(JNIEnv *env, jobject in_bmp, jobject out_bmp);

    int exec_target(JNIEnv *env, jobject in_bmp, jobject out_bmp);

private:
    AAssetManager *asset_manager;
    const char *param_path;
    const char *model_path;
};

#endif//INCLUDE_UPSCALE_ENGINE_H
#ifndef INCLUDE_UPSCALE_ENGINE_H
#define INCLUDE_UPSCALE_ENGINE_H

#include "realcugan.h"

class UpscaleEngine {
public:
    UpscaleEngine();

    ~UpscaleEngine();

    static std::unique_ptr<UpscaleEngine> create(AAssetManager *assetManager, const char *param, const char *model);

    void clear();

    int exec(JNIEnv *env, jobject file_data, const char *out_path, jobject progress);

private:
    std::vector<int> gpuid;
    int use_gpu_count;
    std::vector<RealCUGAN *> realcugan;
};

#endif//INCLUDE_UPSCALE_ENGINE_H
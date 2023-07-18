// realcugan implemented with ncnn library
// ./realcugan-ncnn -i input.png -o output.png  -m models-nose -s 2  -n 0

#include <cstdio>
#include <algorithm>
#include <queue>
#include <vector>
#include <clocale>

#if _WIN32
// image decoder and encoder with wic
#include "wic_image.h"
#else // _WIN32
// image decoder and encoder with stb
#define STB_IMAGE_IMPLEMENTATION
#define STBI_NO_PSD
#define STBI_NO_TGA
#define STBI_NO_GIF
#define STBI_NO_HDR
#define STBI_NO_PIC
#define STBI_NO_STDIO

#include "stb_image.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION

#include "stb_image_write.h"

#endif // _WIN32

#include "webp_image.h"

// ncnn
#include "cpu.h"
#include "gpu.h"
#include "platform.h"

#include "realcugan.h"

#include "filesystem_utils.h"
#include "upscale_engine.h"
#include "Utils.h"

UpscaleEngine::UpscaleEngine() = default;

UpscaleEngine::~UpscaleEngine() = default;

std::unique_ptr<UpscaleEngine>
UpscaleEngine::create(AAssetManager *assetManager, const char *param, const char *model) {
    LOGD("INIT...");
    auto engine = std::make_unique<UpscaleEngine>();

    ncnn::create_gpu_instance();
    if (engine->gpuid.empty()) {
        engine->gpuid.push_back(ncnn::get_default_gpu_index());
    }

    engine->use_gpu_count = (int) engine->gpuid.size();
    LOGD("GPU count : %i", engine->use_gpu_count);

    int gpu_count = ncnn::get_gpu_count();
    for (int i = 0; i < engine->use_gpu_count; i++) {
        if (engine->gpuid[i] < -1 || engine->gpuid[i] >= gpu_count) {
            fprintf(stderr, "invalid gpu device\n");

            ncnn::destroy_gpu_instance();
            return nullptr;
        }
    }

    engine->realcugan = std::vector<RealCUGAN *>(engine->use_gpu_count);

    for (int i = 0; i < engine->use_gpu_count; i++) {
        engine->realcugan[i] = new RealCUGAN(engine->gpuid[i], false, 1);

        int loaded = engine->realcugan[i]->load(assetManager, param, model);
        LOGD("Model loaded %d : %d", i, loaded);
    }
    LOGD("INIT END");

    return engine;
}

void UpscaleEngine::clear() {
    LOGD("CLEARING...");
    for (int i = 0; i < use_gpu_count; i++) {
        delete realcugan[i];
    }
    realcugan.clear();
    ncnn::destroy_gpu_instance();
    LOGD("CLEAR END");
}

int
UpscaleEngine::exec(JNIEnv *env, jobject file_data, const char *out_path, jobject progress) {
    std::vector<int> tilesize;
    int syncgap = 3;
    // Forced model values
    path_t model = PATHSTR("models-nose");
    int scale = 2;
    int noise = 0;

    int prepadding;

    LOGD("UPSCALING...");

    if (model.find(PATHSTR("models-se")) != path_t::npos
        || model.find(PATHSTR("models-nose")) != path_t::npos
        || model.find(PATHSTR("models-pro")) != path_t::npos) {
        if (scale == 2) {
            prepadding = 18;
        }
        if (scale == 3) {
            prepadding = 14;
        }
        if (scale == 4) {
            prepadding = 19;
        }
    } else {
        fprintf(stderr, "unknown model dir type\n");
        return -1;
    }

    if (model.find(PATHSTR("models-nose")) != path_t::npos) {
        // force syncgap off for nose models
        syncgap = 0;
    }


    if (tilesize.empty()) {
        tilesize.resize(use_gpu_count, 0);
    }

    for (int i = 0; i < use_gpu_count; i++) {
        if (tilesize[i] != 0)
            continue;

        if (gpuid[i] == -1) {
            // cpu only
            tilesize[i] = 400;
            continue;
        }

        uint32_t heap_budget = ncnn::get_gpu_device(gpuid[i])->get_heap_budget();

        // more fine-grained tilesize policy here
        if (model.find(PATHSTR("models-nose")) != path_t::npos ||
            model.find(PATHSTR("models-se")) != path_t::npos ||
            model.find(PATHSTR("models-pro")) != path_t::npos) {
            if (scale == 2) {
                if (heap_budget > 1300)
                    tilesize[i] = 400;
                else if (heap_budget > 800)
                    tilesize[i] = 300;
                else if (heap_budget > 400)
                    tilesize[i] = 200;
                else if (heap_budget > 200)
                    tilesize[i] = 100;
                else
                    tilesize[i] = 32;
            }
            if (scale == 3) {
                if (heap_budget > 3300)
                    tilesize[i] = 400;
                else if (heap_budget > 1900)
                    tilesize[i] = 300;
                else if (heap_budget > 950)
                    tilesize[i] = 200;
                else if (heap_budget > 320)
                    tilesize[i] = 100;
                else
                    tilesize[i] = 32;
            }
            if (scale == 4) {
                if (heap_budget > 1690)
                    tilesize[i] = 400;
                else if (heap_budget > 980)
                    tilesize[i] = 300;
                else if (heap_budget > 530)
                    tilesize[i] = 200;
                else if (heap_budget > 240)
                    tilesize[i] = 100;
                else
                    tilesize[i] = 32;
            }
        }
    }

    for (int i = 0; i < use_gpu_count; i++) {
        realcugan[i]->noise = noise;
        realcugan[i]->scale = scale;
        realcugan[i]->tilesize = tilesize[i];
        realcugan[i]->prepadding = prepadding;
        realcugan[i]->syncgap = syncgap;
    }

    // load image
    LOGD("LOADING");
    unsigned char *pixeldata;
    int webp = 0;
    int w;
    int h;
    int c;
    auto *filedata = (unsigned char *) env->GetDirectBufferAddress(file_data);
    int length = (int) env->GetDirectBufferCapacity(file_data);
    LOGD("LOADING 1 %i", length);

    //ncnn::Mat inimage = ncnn::Mat::from_android_bitmap(env, in_bmp, ncnn::Mat::PIXEL_RGBA);
    pixeldata = webp_load(filedata, length, &w, &h, &c);
    if (pixeldata) {
        webp = 1;
    } else {
        // not webp, try jpg png etc.
#if _WIN32
        pixeldata = wic_decode_image(imagepath.c_str(), &w, &h, &c);
#else // _WIN32
        LOGD("LOADING 2");
        pixeldata = stbi_load_from_memory(filedata, length, &w, &h, &c, 0);
        LOGD("LOADING 3 : c %i", c);
        if (pixeldata) {
            // stb_image auto channel
            if (c == 1) {
                LOGD("LOADING 4");
                // grayscale -> rgb
                stbi_image_free(pixeldata);
                pixeldata = stbi_load_from_memory(filedata, length, &w, &h, &c, 3);
                c = 3;
            } else if (c == 2) {
                LOGD("LOADING 5");
                // grayscale + alpha -> rgba
                stbi_image_free(pixeldata);
                pixeldata = stbi_load_from_memory(filedata, length, &w, &h, &c, 4);
                c = 4;
            }
        }
#endif // _WIN32
    }
    if (!pixeldata) {
        LOGE("no pixel data");
        return -1;
    }
    LOGD("LOADED - BUILDING MATRIX c=%i", c);

    ncnn::Mat inimage = ncnn::Mat(w, h, (void *) pixeldata, (size_t) c, c);

    // realcugan proc
    LOGD("PROCESSING");
    auto *progress_c = (unsigned char *) env->GetDirectBufferAddress(progress);
    for (int i = 0; i < use_gpu_count; i++) {
        ncnn::Mat outimage = ncnn::Mat(inimage.w * scale, inimage.h * scale,
                                       (size_t) inimage.elemsize,
                                       (int) inimage.elemsize);
        realcugan[i]->process(inimage, outimage, progress_c);

        // save image
        LOGD("SAVING");
        inimage.release();
        LOGD("target path %s", out_path);
        int success = stbi_write_png(out_path, outimage.w, outimage.h, outimage.elempack,
                                     outimage.data, 0);
        LOGD("success %i", success);
        outimage.release();
        LOGD("outImage released");
    }
    progress_c[0] = 100;
    LOGD("FINALIZED");

    return 0;
}
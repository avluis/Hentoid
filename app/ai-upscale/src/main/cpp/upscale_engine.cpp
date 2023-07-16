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

#if _WIN32
#include <wchar.h>
static wchar_t* optarg = NULL;
static int optind = 1;
static wchar_t getopt(int argc, wchar_t* const argv[], const wchar_t* optstring)
{
    if (optind >= argc || argv[optind][0] != L'-')
        return -1;

    wchar_t opt = argv[optind][1];
    const wchar_t* p = wcschr(optstring, opt);
    if (p == NULL)
        return L'?';

    optarg = NULL;

    if (p[1] == L':')
    {
        optind++;
        if (optind >= argc)
            return L'?';

        optarg = argv[optind];
    }

    optind++;

    return opt;
}

static std::vector<int> parse_optarg_int_array(const wchar_t* optarg)
{
    std::vector<int> array;
    array.push_back(_wtoi(optarg));

    const wchar_t* p = wcschr(optarg, L',');
    while (p)
    {
        p++;
        array.push_back(_wtoi(p));
        p = wcschr(p, L',');
    }

    return array;
}
#else // _WIN32

#include <unistd.h> // getopt()

static std::vector<int> parse_optarg_int_array(const char *optarg) {
    std::vector<int> array;
    array.push_back(atoi(optarg));

    const char *p = strchr(optarg, ',');
    while (p) {
        p++;
        array.push_back(atoi(p));
        p = strchr(p, ',');
    }

    return array;
}

#endif // _WIN32

// ncnn
#include "cpu.h"
#include "gpu.h"
#include "platform.h"

#include "realcugan.h"

#include "filesystem_utils.h"
#include "upscale_engine.h"
#include "Utils.h"

static void print_usage() {
    fprintf(stdout, "Usage: realcugan-ncnn -i infile -o outfile [options]...\n\n");
    fprintf(stdout, "  -h                   show this help\n");
    fprintf(stdout, "  -v                   verbose output\n");
    fprintf(stdout, "  -i input-path        input image path (jpg/png/webp) or directory\n");
    fprintf(stdout, "  -o output-path       output image path (jpg/png/webp) or directory\n");
    fprintf(stdout, "  -n noise-level       denoise level (-1/0/1/2/3, default=-1)\n");
    fprintf(stdout, "  -s scale             upscale ratio (1/2/3/4, default=2)\n");
    fprintf(stdout,
            "  -t tile-size         tile size (>=32/0=auto, default=0) can be 0,0,0 for multi-gpu\n");
    fprintf(stdout, "  -c syncgap-mode      sync gap mode (0/1/2/3, default=3)\n");
    fprintf(stdout, "  -m model-path        realcugan model path (default=models-se)\n");
    fprintf(stdout,
            "  -g gpu-id            gpu device to use (-1=cpu, default=auto) can be 0,1,2 for multi-gpu\n");
    fprintf(stdout,
            "  -j load:proc:save    thread count for load/proc/save (default=1:2:2) can be 1:2,2,2:2 for multi-gpu\n");
    fprintf(stdout, "  -x                   enable tta mode\n");
    fprintf(stdout, "  -f format            output image format (jpg/png/webp, default=ext/png)\n");
}

class Task {
public:
    int id;
//    int webp;
    int scale;

//    path_t inpath;
//    path_t outpath;

    ncnn::Mat inimage;
    ncnn::Mat outimage;
};

class TaskQueue {
public:
    TaskQueue() {
    }

    void put(const Task &v) {
        lock.lock();

        while (tasks.size() >= 8) // FIXME hardcode queue length
        {
            condition.wait(lock);
        }

        tasks.push(v);

        lock.unlock();

        condition.signal();
    }

    void get(Task &v) {
        lock.lock();

        while (tasks.size() == 0) {
            condition.wait(lock);
        }

        v = tasks.front();
        tasks.pop();

        lock.unlock();

        condition.signal();
    }

private:
    ncnn::Mutex lock;
    ncnn::ConditionVariable condition;
    std::queue<Task> tasks;
};

TaskQueue toproc;
TaskQueue tosave;

class LoadThreadParams {
public:
    JNIEnv *env;
    jobject bitmap;
    int scale;
    int jobs_load;

    // session data
    std::vector<path_t> input_files;
    std::vector<path_t> output_files;
};

void *loadFromMemory(void *args) {
    const auto *ltp = (const LoadThreadParams *) args;
    const int scale = ltp->scale;
    JNIEnv *env = ltp->env;
    jobject bitmap = ltp->bitmap;

    Task v;
    v.id = 0; // Index of loaded file; hardcoded to 0 for now
//        v.webp = webp;
    v.scale = scale;
//        v.inpath = imagepath;
//        v.outpath = ltp->output_files[i];

    v.inimage = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGBA);

/*
    // Get bitmap info
    AndroidBitmapInfo info;
    LOGD("getinfo %i", AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS);
    LOGD("format %i", info.format == ANDROID_BITMAP_FORMAT_RGBA_8888);
    LOGD("stride %i", info.stride % 4 == 0);

    // Copy bitmap pixels to the buffer memory
    void *bitmapData = nullptr;
    LOGD("lock pixels %i", AndroidBitmap_lockPixels(env, bitmap, &bitmapData) ==
                           ANDROID_BITMAP_RESULT_SUCCESS);

    Task v;
    v.id = 0; // Index of loaded file; hardcoded to 0 for now
//        v.webp = webp;
    v.scale = scale;
//        v.inpath = imagepath;
//        v.outpath = ltp->output_files[i];
    int c = 4; // RGBA

    LOGD("info %ix%i", info.width, info.height);
    v.inimage = ncnn::Mat(info.width, info.height, bitmapData, (size_t) c, c);

    LOGD("inimage empty %i", v.inimage.empty());
//    path_t ext = get_file_extension(v.outpath);

    AndroidBitmap_unlockPixels(env, bitmap);
    */

    toproc.put(v);

    return 0;
}

class ProcThreadParams {
public:
    const RealCUGAN *realcugan;
};

void *proc(void *args) {
    const ProcThreadParams *ptp = (const ProcThreadParams *) args;
    const RealCUGAN *realcugan = ptp->realcugan;

    for (;;) {
        Task v;

        toproc.get(v);

        if (v.id == -233)
            break;

        const int scale = v.scale;
        LOGD("PROCESS BEGIN; scale=%i", scale);
        if (scale == 1) {
            v.outimage = ncnn::Mat(v.inimage.w, v.inimage.h, (size_t) v.inimage.elemsize,
                                   (int) v.inimage.elemsize);
            realcugan->process(v.inimage, v.outimage);

            tosave.put(v);
            continue;
        }

        v.outimage = ncnn::Mat(v.inimage.w * scale, v.inimage.h * scale,
                               (size_t) v.inimage.elemsize, (int) v.inimage.elemsize);
        realcugan->process(v.inimage, v.outimage);

        tosave.put(v);
    }
    LOGD("PROCESS END");

    return 0;
}

class SaveThreadParams {
public:
    JNIEnv *env;
    jobject out_bmp;
};

void *save(void *args) {
    const auto *stp = (const SaveThreadParams *) args;
    JNIEnv *env = stp->env;
    jobject out_bmp = stp->out_bmp;

    //for (;;) {
    Task v;

    tosave.get(v);

    /* TODO
    if (v.id == -233)
        break;
        */

    const int channels = v.inimage.elempack;

    LOGD("save result (%i channels)...", channels);
//        long begin = clock();

    // free input pixel data
    {
        v.inimage.release();
        /*
        auto *pixeldata = (unsigned char *) v.inimage.data;
#if _WIN32
        free(pixeldata);
#else
        stbi_image_free(pixeldata);
#endif
         */
    }

    LOGD("inImage released");

    /*
    int result;
    AndroidBitmapInfo srcInfo;

    result = AndroidBitmap_getInfo(env, out_bmp, &srcInfo);
    if (result != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("load : success KO");
        return nullptr;
    }

    int width = srcInfo.width;
    int height = srcInfo.height;

    LOGD("...into out BMP : %ix%i / %i", width, height, srcInfo.format);
    LOGD("outimage %ix%i / %lu", v.outimage.w, v.outimage.h, v.outimage.elemsize);
     */

    v.outimage.to_android_bitmap(env, out_bmp, ncnn::Mat::PIXEL_RGBA);

    LOGD("AA1");
//        v.outimage.release();
    out_bmp = nullptr;

    /*
    path_t ext = get_file_extension(v.outpath);

    if (ext == PATHSTR("webp") || ext == PATHSTR("WEBP")) {
        success = webp_save(v.outpath.c_str(), v.outimage.w, v.outimage.h, v.outimage.elempack,
                            (const unsigned char *) v.outimage.data);
    } else if (ext == PATHSTR("png") || ext == PATHSTR("PNG")) {
#if _WIN32
        success = wic_encode_image(v.outpath.c_str(), v.outimage.w, v.outimage.h, v.outimage.elempack, v.outimage.data);
#else
        success = stbi_write_png(v.outpath.c_str(), v.outimage.w, v.outimage.h,
                                 v.outimage.elempack, v.outimage.data, 0);
#endif
    } else if (ext == PATHSTR("jpg") || ext == PATHSTR("JPG") || ext == PATHSTR("jpeg") ||
               ext == PATHSTR("JPEG")) {
#if _WIN32
        success = wic_encode_jpeg_image(v.outpath.c_str(), v.outimage.w, v.outimage.h, v.outimage.elempack, v.outimage.data);
#else
        success = stbi_write_jpg(v.outpath.c_str(), v.outimage.w, v.outimage.h,
                                 v.outimage.elempack, v.outimage.data, 100);
#endif
     }

    if (success) {
        long end = clock();
        LOGE("save result use time: %.3f\n", (end - begin) * 1.0 / CLOCKS_PER_SEC);

        if (verbose) {
#if _WIN32
            fwprintf(stdout, L"%ls -> %ls done\n", v.inpath.c_str(), v.outpath.c_str());
#else
            LOGD("%s -> %s done\n", v.inpath.c_str(), v.outpath.c_str());
#endif
        }
    } else {
#if _WIN32
        fwprintf(stderr, L"encode image %ls failed\n", v.outpath.c_str());
#else
        LOGE("encode image %s failed\n", v.outpath.c_str());
#endif
    }
                       */
    //}

    return 0;
}


UpscaleEngine::UpscaleEngine() = default;

UpscaleEngine::~UpscaleEngine() = default;

void UpscaleEngine::useModelAssets(AAssetManager *assetMgr, const char *param, const char *model) {
    this->asset_manager = assetMgr;
    this->param_path = param;
    this->model_path = model;
}

int UpscaleEngine::exec(JNIEnv *env, jobject in_bmp, jobject out_bmp) {
    std::vector<int> tilesize;
    std::vector<int> gpuid;
    int syncgap = 3;
    int tta_mode = 0;
    // Forced model values
    path_t model = PATHSTR("models-nose");
    int scale = 2;
    int noise = 0;

    int prepadding = 0;

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

    ncnn::create_gpu_instance();

    if (gpuid.empty()) {
        gpuid.push_back(ncnn::get_default_gpu_index());
    }

    const int use_gpu_count = (int) gpuid.size();
    LOGD("GPU count : %i", use_gpu_count);

    if (tilesize.empty()) {
        tilesize.resize(use_gpu_count, 0);
    }

    int cpu_count = std::max(1, ncnn::get_cpu_count());

    int gpu_count = ncnn::get_gpu_count();
    for (int i = 0; i < use_gpu_count; i++) {
        if (gpuid[i] < -1 || gpuid[i] >= gpu_count) {
            fprintf(stderr, "invalid gpu device\n");

            ncnn::destroy_gpu_instance();
            return -1;
        }
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

    {
        std::vector<RealCUGAN *> realcugan(use_gpu_count);

        for (int i = 0; i < use_gpu_count; i++) {
            realcugan[i] = new RealCUGAN(gpuid[i], tta_mode, 1);

            int loaded = realcugan[i]->load(asset_manager, param_path, model_path);
            LOGD("Model loaded %d : %d", i, loaded);

            realcugan[i]->noise = noise;
            realcugan[i]->scale = scale;
            realcugan[i]->tilesize = tilesize[i];
            realcugan[i]->prepadding = prepadding;
            realcugan[i]->syncgap = syncgap;
        }

        // main routine
        {
            // load image
            LOGD("LOADING");
            ncnn::Mat inimage = ncnn::Mat::from_android_bitmap(env, in_bmp, ncnn::Mat::PIXEL_RGBA);

            // realcugan proc
            LOGD("PROCESSING");
            int targetW = inimage.w * scale;
            int targetH = inimage.h * scale;
            for (int i = 0; i < use_gpu_count; i++) {
                ncnn::Mat outimage = ncnn::Mat(targetW, targetH, (size_t) inimage.elemsize,
                                               (int) inimage.elemsize);
                realcugan[i]->process(inimage, outimage);
                /*
                auto *pixels = new unsigned char[targetW * targetH * 4 * inimage.elemsize]();
                inimage.to_pixels_resize(pixels, ncnn::Mat::PIXEL_RGBA, targetW, targetH);
                ncnn::Mat outimage = ncnn::Mat::from_pixels(pixels, ncnn::Mat::PIXEL_RGBA, targetW, targetH);
                 */

                // save image
                LOGD("SAVING");
                inimage.release();
                LOGD("inImage released");
                outimage.to_android_bitmap(env, out_bmp, ncnn::Mat::PIXEL_RGBA);
                LOGD("data copied");
                outimage.release();
                LOGD("outImage released");
            }
        }

        LOGD("FINALIZING 1");
        for (int i = 0; i < use_gpu_count; i++) {
            delete realcugan[i];
        }
        LOGD("FINALIZING 2");
        realcugan.clear();
    }

    LOGD("FINALIZING 3");
    ncnn::destroy_gpu_instance();
    LOGD("FINALIZED");

    /*
    // Process
    int targetW = inimage.w * scale;
    int targetH = inimage.h * scale;
    //ncnn::Mat outimage = ncnn::Mat(targetW, targetH, (size_t) inimage.elemsize,
    //                               (int) inimage.elemsize);

    LOGD("process %zu", inimage.elemsize);

    auto *pixels = new unsigned char[targetW * targetH * 4 * inimage.elemsize]();
    inimage.to_pixels_resize(pixels, ncnn::Mat::PIXEL_RGBA, targetW, targetH);
    ncnn::Mat outimage = ncnn::Mat::from_pixels(pixels, ncnn::Mat::PIXEL_RGBA, targetW, targetH);

    LOGD("out %ix%i", outimage.w, outimage.h);

    // Save
    inimage.release();
    LOGD("inImage released");
    outimage.to_android_bitmap(env, out_bmp, ncnn::Mat::PIXEL_RGBA);
    LOGD("data copied");
    outimage.release();
    LOGD("outImage released");
    LOGD("END");
     */

    return 0;
}

int UpscaleEngine::exec_target(JNIEnv *env, jobject in_bmp, jobject out_bmp) {
    /*
    path_t inputpath;
    path_t outputpath;
    int noise = -1;
    int scale = 2;
    */
    std::vector<int> tilesize;
    path_t model = PATHSTR("models-nose");
    std::vector<int> gpuid;
    int jobs_load = 1;
    std::vector<int> jobs_proc;
    int jobs_save = 2;
    int verbose = 0;
    int syncgap = 3;
    int tta_mode = 0;
    path_t format = PATHSTR("png");

    // Forced values
    int scale = 2;
    int noise = 0;

    if (noise < -1 || noise > 3) {
        fprintf(stderr, "invalid noise argument\n");
        return -1;
    }

    if (!(scale == 1 || scale == 2 || scale == 3 || scale == 4)) {
        fprintf(stderr, "invalid scale argument\n");
        return -1;
    }

    if (tilesize.size() != (gpuid.empty() ? 1 : gpuid.size()) && !tilesize.empty()) {
        fprintf(stderr, "invalid tilesize argument\n");
        return -1;
    }

    if (!(syncgap == 0 || syncgap == 1 || syncgap == 2 || syncgap == 3)) {
        fprintf(stderr, "invalid syncgap argument\n");
        return -1;
    }

    for (int i: tilesize) {
        if (i != 0 && i < 32) {
            fprintf(stderr, "invalid tilesize argument\n");
            return -1;
        }
    }

    if (jobs_load < 1 || jobs_save < 1) {
        fprintf(stderr, "invalid thread count argument\n");
        return -1;
    }

    if (jobs_proc.size() != (gpuid.empty() ? 1 : gpuid.size()) && !jobs_proc.empty()) {
        fprintf(stderr, "invalid jobs_proc thread count argument\n");
        return -1;
    }

    for (int i: jobs_proc) {
        if (i < 1) {
            fprintf(stderr, "invalid jobs_proc thread count argument\n");
            return -1;
        }
    }

    /*
    if (!path_is_directory(outputpath)) {
        // guess format from outputpath no matter what format argument specified
        path_t ext = get_file_extension(outputpath);

        if (ext == PATHSTR("png") || ext == PATHSTR("PNG")) {
            format = PATHSTR("png");
        } else if (ext == PATHSTR("webp") || ext == PATHSTR("WEBP")) {
            format = PATHSTR("webp");
        } else if (ext == PATHSTR("jpg") || ext == PATHSTR("JPG") || ext == PATHSTR("jpeg") ||
                   ext == PATHSTR("JPEG")) {
            format = PATHSTR("jpg");
        } else {
            fprintf(stderr, "invalid outputpath extension type\n");
            return -1;
        }
    }

    if (format != PATHSTR("png") && format != PATHSTR("webp") && format != PATHSTR("jpg")) {
        fprintf(stderr, "invalid format argument\n");
        return -1;
    }

    // collect input and output filepath
    std::vector<path_t> input_files;
    std::vector<path_t> output_files;
    {
        if (path_is_directory(inputpath) && path_is_directory(outputpath)) {
            std::vector<path_t> filenames;
            int lr = list_directory(inputpath, filenames);
            if (lr != 0)
                return -1;

            const int count = filenames.size();
            input_files.resize(count);
            output_files.resize(count);

            path_t last_filename;
            path_t last_filename_noext;
            for (int i = 0; i < count; i++) {
                path_t filename = filenames[i];
                path_t filename_noext = get_file_name_without_extension(filename);
                path_t output_filename = filename_noext + PATHSTR('.') + format;

                // filename list is sorted, check if output image path conflicts
                if (filename_noext == last_filename_noext) {
                    path_t output_filename2 = filename + PATHSTR('.') + format;
#if _WIN32
                    fwprintf(stderr, L"both %ls and %ls output %ls ! %ls will output %ls\n", filename.c_str(), last_filename.c_str(), output_filename.c_str(), filename.c_str(), output_filename2.c_str());
#else
                    fprintf(stderr, "both %s and %s output %s ! %s will output %s\n",
                            filename.c_str(), last_filename.c_str(), output_filename.c_str(),
                            filename.c_str(), output_filename2.c_str());
#endif
                    output_filename = output_filename2;
                } else {
                    last_filename = filename;
                    last_filename_noext = filename_noext;
                }

                input_files[i] = inputpath + PATHSTR('/') + filename;
                output_files[i] = outputpath + PATHSTR('/') + output_filename;
            }
        } else if (!path_is_directory(inputpath) && !path_is_directory(outputpath)) {
            input_files.push_back(inputpath);
            output_files.push_back(outputpath);
        } else {
            fprintf(stderr,
                    "inputpath and outputpath must be either file or directory at the same time\n");
            return -1;
        }
    }
     */

    int prepadding = 0;

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
/*
#if _WIN32
    wchar_t parampath[256];
    wchar_t modelpath[256];
    if (noise == -1)
    {
        swprintf(parampath, 256, L"%s/up%dx-conservative.param", model.c_str(), scale);
        swprintf(modelpath, 256, L"%s/up%dx-conservative.bin", model.c_str(), scale);
    }
    else if (noise == 0)
    {
        swprintf(parampath, 256, L"%s/up%dx-no-denoise.param", model.c_str(), scale);
        swprintf(modelpath, 256, L"%s/up%dx-no-denoise.bin", model.c_str(), scale);
    }
    else
    {
        swprintf(parampath, 256, L"%s/up%dx-denoise%dx.param", model.c_str(), scale, noise);
        swprintf(modelpath, 256, L"%s/up%dx-denoise%dx.bin", model.c_str(), scale, noise);
    }
#else
    char parampath[256];
    char modelpath[256];
    if (noise == -1) {
        sprintf(parampath, "%s/up%dx-conservative.param", model.c_str(), scale);
        sprintf(modelpath, "%s/up%dx-conservative.bin", model.c_str(), scale);
    } else if (noise == 0) {
        sprintf(parampath, "%s/up%dx-no-denoise.param", model.c_str(), scale);
        sprintf(modelpath, "%s/up%dx-no-denoise.bin", model.c_str(), scale);
    } else {
        sprintf(parampath, "%s/up%dx-denoise%dx.param", model.c_str(), scale, noise);
        sprintf(modelpath, "%s/up%dx-denoise%dx.bin", model.c_str(), scale, noise);
    }
#endif

#if _WIN32
    CoInitializeEx(NULL, COINIT_MULTITHREADED);
#endif
*/
    ncnn::create_gpu_instance();

    if (gpuid.empty()) {
        gpuid.push_back(ncnn::get_default_gpu_index());
    }

    const int use_gpu_count = (int) gpuid.size();

    if (jobs_proc.empty()) {
        jobs_proc.resize(use_gpu_count, 2);
    }

    if (tilesize.empty()) {
        tilesize.resize(use_gpu_count, 0);
    }

    int cpu_count = std::max(1, ncnn::get_cpu_count());
    jobs_load = std::min(jobs_load, cpu_count);
    jobs_save = std::min(jobs_save, cpu_count);

    int gpu_count = ncnn::get_gpu_count();
    for (int i = 0; i < use_gpu_count; i++) {
        if (gpuid[i] < -1 || gpuid[i] >= gpu_count) {
            fprintf(stderr, "invalid gpu device\n");

            ncnn::destroy_gpu_instance();
            return -1;
        }
    }

    int total_jobs_proc = 0;
    int jobs_proc_per_gpu[16] = {0};
    for (int i = 0; i < use_gpu_count; i++) {
        if (gpuid[i] == -1) {
            jobs_proc[i] = std::min(jobs_proc[i], cpu_count);
            total_jobs_proc += 1;
        } else {
            total_jobs_proc += jobs_proc[i];
            jobs_proc_per_gpu[gpuid[i]] += jobs_proc[i];
        }
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

        /*
        if (path_is_directory(inputpath) && path_is_directory(outputpath)) {
            // multiple gpu jobs share the same heap
            heap_budget /= jobs_proc_per_gpu[gpuid[i]];
        }
         */

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

    {
        std::vector<RealCUGAN *> realcugan(use_gpu_count);

        for (int i = 0; i < use_gpu_count; i++) {
            int num_threads = gpuid[i] == -1 ? jobs_proc[i] : 1;

            realcugan[i] = new RealCUGAN(gpuid[i], tta_mode, num_threads);

            int loaded = realcugan[i]->load(asset_manager, param_path, model_path);
            LOGD("Model loaded %d : %d", i, loaded);

            realcugan[i]->noise = noise;
            realcugan[i]->scale = scale;
            realcugan[i]->tilesize = tilesize[i];
            realcugan[i]->prepadding = prepadding;
            realcugan[i]->syncgap = syncgap;
        }

        // main routine
        {
            // load image
            LoadThreadParams ltp;
            ltp.env = env;
            ltp.bitmap = in_bmp;
            ltp.scale = scale;
            ltp.jobs_load = jobs_load;

            //ncnn::Thread load_thread(load, (void *) &ltp);
            ncnn::Thread load_thread(loadFromMemory, (void *) &ltp);

            // realcugan proc
            std::vector<ProcThreadParams> ptp(use_gpu_count);
            for (int i = 0; i < use_gpu_count; i++) {
                ptp[i].realcugan = realcugan[i];
            }

            LOGD("PROCESSING");
            std::vector<ncnn::Thread *> proc_threads(total_jobs_proc);
            {
                int total_jobs_proc_id = 0;
                for (int i = 0; i < use_gpu_count; i++) {
                    if (gpuid[i] == -1) {
                        proc_threads[total_jobs_proc_id++] = new ncnn::Thread(proc,
                                                                              (void *) &ptp[i]);
                    } else {
                        for (int j = 0; j < jobs_proc[i]; j++) {
                            proc_threads[total_jobs_proc_id++] = new ncnn::Thread(proc,
                                                                                  (void *) &ptp[i]);
                        }
                    }
                }
            }

            // save image
            LOGD("SAVING");
            SaveThreadParams stp{};
            stp.out_bmp = out_bmp;
            stp.env = env;

            std::vector<ncnn::Thread *> save_threads(jobs_save);
            for (int i = 0; i < jobs_save; i++) {
                save_threads[i] = new ncnn::Thread(save, (void *) &stp);
            }

            // end
            LOGD("FINALIZING 1");
            load_thread.join();

            LOGD("FINALIZING 2");
            Task end;
            end.id = -233;

            for (int i = 0; i < total_jobs_proc; i++) {
                toproc.put(end);
            }
            LOGD("FINALIZING 3");

            for (int i = 0; i < total_jobs_proc; i++) {
                proc_threads[i]->join();
                delete proc_threads[i];
            }
            LOGD("FINALIZING 4");

            for (int i = 0; i < jobs_save; i++) {
                tosave.put(end);
            }
            LOGD("FINALIZING 5");

            for (int i = 0; i < jobs_save; i++) {
                save_threads[i]->join();
                delete save_threads[i];
            }
            LOGD("FINALIZING 6");
        }

        for (int i = 0; i < use_gpu_count; i++) {
            delete realcugan[i];
        }
        LOGD("FINALIZING 7");
        realcugan.clear();
    }

    LOGD("FINALIZING 8");
    ncnn::destroy_gpu_instance();
    LOGD("FINALIZED");

    return 0;
}
#include <jni.h>
#include <string>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "upscale_engine.h"

namespace {

    UpscaleEngine *castToUpscaleEngine(jlong handle) {
        return reinterpret_cast<UpscaleEngine *>(static_cast<uintptr_t>(handle));
    }

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_me_devsaki_hentoid_ai_1upscale_AiUpscaler_initEngine(
        JNIEnv *env,
        jobject /* this */,
        jobject assetMgr,
        jstring param,
        jstring model) {
    AAssetManager *mgr = AAssetManager_fromJava(env, assetMgr);
    const char *paramC = env->GetStringUTFChars(param, nullptr);
    const char *modelC = env->GetStringUTFChars(model, nullptr);

    auto engine = UpscaleEngine::create(mgr, paramC, modelC);
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(engine.release()));
}

extern "C" JNIEXPORT void JNICALL
Java_me_devsaki_hentoid_ai_1upscale_AiUpscaler_clear(JNIEnv * /* env */,
                                                     jobject /* this */,
                                                     jlong engine_handle) {
    if (engine_handle == 0L) return;
    castToUpscaleEngine(engine_handle)->clear();
    delete castToUpscaleEngine(engine_handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_me_devsaki_hentoid_ai_1upscale_AiUpscaler_upscale(
        JNIEnv *env,
        jobject /* this */,
        jlong engine_handle,
        jobject data_in,
        jstring out_path,
        jobject progress) {
    if (engine_handle == 0L) return -1;
    const char *out_pathC = env->GetStringUTFChars(out_path, nullptr);

    return castToUpscaleEngine(engine_handle)->exec(env, data_in, out_pathC, progress);
}
#include <jni.h>
#include <string>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "upscale_engine.h"

extern "C" JNIEXPORT jint JNICALL
Java_me_devsaki_hentoid_ai_1upscale_NativeLib_upscale(
        JNIEnv *env,
        jobject /* this */,
        jobject assetMgr,
        jstring param,
        jstring model,
        jobject in_bmp,
        jobject out_bmp,
        jobject progress) {
    // TODO progress
    AAssetManager *mgr = AAssetManager_fromJava(env, assetMgr);
    const char *paramC = env->GetStringUTFChars(param, nullptr);
    const char *modelC = env->GetStringUTFChars(model, nullptr);

    auto *engine = new UpscaleEngine();
    engine->useModelAssets(mgr, paramC, modelC);
    return engine->exec(env, in_bmp, out_bmp);
}
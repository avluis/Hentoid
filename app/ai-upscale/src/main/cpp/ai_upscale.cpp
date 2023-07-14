#include <jni.h>
#include <string>
#include "upscale_engine.h"

extern "C" JNIEXPORT jstring JNICALL
Java_me_devsaki_hentoid_ai_1upscale_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";

    auto* engine = new UpscaleEngine();
    UpscaleEngine::exec(0,"");

    return env->NewStringUTF(hello.c_str());
}
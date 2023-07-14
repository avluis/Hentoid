#ifndef AI_UPSCALE_UTILS_H
#define AI_UPSCALE_UTILS_H

#include <android/log.h>

// Macros for logging
#define LOG_TAG "AI_UPSCALE"
#define LOG(severity, ...) ((void)__android_log_print(ANDROID_LOG_##severity, LOG_TAG, __VA_ARGS__))
#define LOGE(...) LOG(ERROR, __VA_ARGS__)
#define LOGD(...) LOG(DEBUG, __VA_ARGS__)
#define LOGV(...) LOG(VERBOSE, __VA_ARGS__)

// Log an error and return false if condition fails
#define RET_CHECK(condition)                                                    \
    do {                                                                        \
        if (!(condition)) {                                                     \
            LOGE("Check failed at %s:%u - %s", __FILE__, __LINE__, #condition); \
            return false;                                                       \
        }                                                                       \
    } while (0)

#endif  // AI_UPSCALE_UTILS_H

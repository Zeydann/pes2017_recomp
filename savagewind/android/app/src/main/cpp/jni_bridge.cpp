// JNI entry point for the Android host shell.
//
// The runtime (runtime/*.c, runtime/nvn/**) is not linked yet. When the Android
// platform layer lands, this file becomes the bridge: it will hand the runtime
// an ANativeWindow, a data root and the thermal/quality callbacks, and forward
// lifecycle events. Until then it reports only what is true about itself.

#include <jni.h>

#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_org_savagewind_port_MainActivity_nativeBuildInfo(JNIEnv *env, jobject /*thiz*/) {
#if defined(__aarch64__)
    const char *abi = "arm64-v8a";
#elif defined(__arm__)
    const char *abi = "armeabi-v7a";
#elif defined(__x86_64__)
    const char *abi = "x86_64";
#else
    const char *abi = "unknown";
#endif

    const std::string info = std::string("native: savagewind_host (") + __DATE__ + ")\n" +
                             "abi: " + abi + "\n" +
                             "status: host shell only — runtime not linked";
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM * /*vm*/, void * /*reserved*/) {
    return JNI_VERSION_1_6;
}

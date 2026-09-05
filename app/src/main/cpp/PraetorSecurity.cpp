#include <jni.h>
#include <string>

// P.R.A.E.T.O.R. Security Layer
// Obfuscation: XOR string to prevent simple strings/hex search in binary.
// Key: 'K' = 0x4B

std::string deobfuscate(const unsigned char* obf, int len) {
    std::string out;
    for (int i = 0; i < len; i++) {
        out += (char)(obf[i] ^ 0x4B);
    }
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_eu_kodanetwork_mchost_security_PraetorSecurity_getSupabaseUrl(
        JNIEnv* env,
        jclass /* clazz */) {
    unsigned char obf[] = {
        0x23, 0x3f, 0x3f, 0x3b, 0x38, 0x71, 0x64, 0x64, 0x38, 0x28, 0x38, 0x2e, 0x31, 0x3b, 0x2d, 0x39, 0x39, 0x26, 0x3b, 0x32, 0x3e, 0x2a, 0x3b, 0x29, 0x27, 0x29, 0x33, 0x20, 0x65, 0x38, 0x3e, 0x3b, 0x2a, 0x29, 0x2a, 0x38, 0x2e, 0x65, 0x28, 0x24
    };
    std::string res = deobfuscate(obf, sizeof(obf));
    return env->NewStringUTF(res.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_eu_kodanetwork_mchost_security_PraetorSecurity_getSupabaseKey(
        JNIEnv* env,
        jclass /* clazz */) {
    unsigned char obf[] = {
        0x2e, 0x32, 0x1, 0x23, 0x29, 0xc, 0x28, 0x22, 0x4, 0x22, 0x1, 0x2, 0x1e, 0x31, 0x2, 0x7a, 0x5, 0x22, 0x2, 0x38, 0x2, 0x25, 0x19, 0x7e, 0x28, 0x8, 0x2, 0x7d, 0x2, 0x20, 0x3b, 0x13, 0x1d, 0x8, 0x1, 0x72, 0x65, 0x2e, 0x32, 0x1, 0x3b, 0x28, 0x78, 0x6, 0x22, 0x4, 0x22, 0x1, 0x31, 0x2f, 0x13, 0x9, 0x23, 0x12, 0x26, 0xd, 0x31, 0x11, 0x18, 0x2, 0x38, 0x2, 0x25, 0x1, 0x27, 0x11, 0x22, 0x2, 0x7d, 0x2, 0x25, 0x5, 0x21, 0x28, 0x79, 0x1d, 0x7d, 0x28, 0xc, 0x11, 0x32, 0x28, 0x26, 0x7a, 0x3c, 0x2e, 0x13, 0x1d, 0x23, 0x28, 0xc, 0x1, 0x38, 0x12, 0x25, 0x23, 0x39, 0x2, 0x22, 0x3c, 0x22, 0x28, 0x26, 0x72, 0x38, 0x11, 0x18, 0x2, 0x7d, 0x2, 0x26, 0xd, 0x3e, 0x29, 0x79, 0x7f, 0x22, 0x7, 0x8, 0x1, 0x3b, 0x12, 0x13, 0x1a, 0x22, 0x4, 0x21, 0xe, 0x78, 0x5, 0x31, 0x12, 0x7e, 0x5, 0xf, 0xe, 0x7f, 0x6, 0x21, 0x12, 0x38, 0x2, 0x26, 0x1d, 0x7f, 0x28, 0x8, 0x2, 0x7d, 0x6, 0x21, 0xa, 0x7e, 0x6, 0x21, 0x1e, 0x33, 0x5, 0x31, 0x2c, 0x32, 0x5, 0x25, 0x7b, 0x65, 0x39, 0x3, 0x39, 0x24, 0x7d, 0x20, 0x1a, 0x3b, 0x13, 0x3, 0xa, 0x25, 0x33, 0xe, 0x2a, 0xd, 0x33, 0x24, 0x31, 0x12, 0x31, 0x38, 0x0, 0x12, 0x73, 0x2, 0x3, 0x2, 0x1e, 0x27, 0x24, 0x3f, 0x66, 0x7c, 0x66, 0x1a, 0x7f, 0x7, 0x5, 0x29, 0x11, 0x1f, 0x73
    };
    std::string res = deobfuscate(obf, sizeof(obf));
    return env->NewStringUTF(res.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_eu_kodanetwork_mchost_security_PraetorSecurity_getFrpcToken(
        JNIEnv* env,
        jclass /* clazz */) {
    unsigned char obf[] = {0x33, 0x12, 0x73, 0x6a, 0x1b, 0x14, 0x20, 0x79, 0x72, 0x2f, 0x07, 0x3a, 0x06, 0x68, 0x31};
    std::string res = deobfuscate(obf, sizeof(obf));
    return env->NewStringUTF(res.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_eu_kodanetwork_mchost_security_PraetorSecurity_getOpenRouterKey(
        JNIEnv* env,
        jclass /* clazz */) {
    unsigned char obf[] = { 0x38, 0x20, 0x66, 0x24, 0x39, 0x66, 0x3d, 0x7a, 0x66, 0x72, 0x72, 0x72, 0x7c, 0x73, 0x7f, 0x7a, 0x7c, 0x78, 0x7c, 0x2e, 0x2d, 0x2f, 0x79, 0x2a, 0x7a, 0x72, 0x7d, 0x2a, 0x2f, 0x7b, 0x28, 0x2a, 0x7c, 0x7a, 0x7e, 0x28, 0x7f, 0x2d, 0x28, 0x2e, 0x2f, 0x2a, 0x28, 0x79, 0x7c, 0x79, 0x28, 0x7e, 0x7d, 0x2f, 0x7b, 0x72, 0x7f, 0x2f, 0x7a, 0x7b, 0x7d, 0x29, 0x78, 0x2e, 0x2d, 0x7d, 0x2a, 0x2e, 0x7e, 0x7e, 0x7d, 0x2d, 0x7d, 0x78, 0x7a, 0x7f, 0x7a };
    std::string res = deobfuscate(obf, sizeof(obf));
    return env->NewStringUTF(res.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_eu_kodanetwork_mchost_security_PraetorSecurity_getBoreHost(
        JNIEnv* env,
        jclass /* clazz */) {
    unsigned char obf[] = {0x73, 0x7e, 0x65, 0x79, 0x7a, 0x7e, 0x65, 0x7a, 0x73, 0x7b, 0x65, 0x73, 0x7c};
    std::string res = deobfuscate(obf, sizeof(obf));
    return env->NewStringUTF(res.c_str());
}

#include <sys/inotify.h>
#include <unistd.h>
#include <thread>
#include <android/log.h>

#define LOG_TAG "PraetorSecurityNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static JavaVM *gJvm = nullptr;
static jclass gAntiTamperClass = nullptr;
static jmethodID gBanMethod = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    gJvm = vm;
    return JNI_VERSION_1_6;
}

void checkTracerPidThread() {
    while (true) {
        FILE *fp = fopen("/proc/self/status", "r");
        if (fp != nullptr) {
            char line[256];
            while (fgets(line, sizeof(line), fp)) {
                if (strncmp(line, "TracerPid:", 10) == 0) {
                    int tracerPid = atoi(&line[10]);
                    if (tracerPid != 0) {
                        LOGE("DEBUGGER ATTACHED! TracerPid: %d", tracerPid);
                        // Trigger Ban
                        JNIEnv *env;
                        int getEnvStat = gJvm->GetEnv((void **)&env, JNI_VERSION_1_6);
                        bool attached = false;
                        if (getEnvStat == JNI_EDETACHED) {
                            if (gJvm->AttachCurrentThread(&env, nullptr) == 0) {
                                attached = true;
                            }
                        }
                        if (gAntiTamperClass != nullptr && gBanMethod != nullptr) {
                            jstring reason = env->NewStringUTF("DEBUGGER_ATTACHED");
                            env->CallStaticVoidMethod(gAntiTamperClass, gBanMethod, reason);
                            env->DeleteLocalRef(reason);
                        }
                        if (attached) {
                            gJvm->DetachCurrentThread();
                        }
                        // Kill self instantly to prevent reverse engineering
                        kill(getpid(), SIGKILL);
                    }
                    break;
                }
            }
            fclose(fp);
        }
        std::this_thread::sleep_for(std::chrono::seconds(2));
    }
}

void inotifyWatcherThread(std::vector<std::string> filesToWatch) {
    LOGI("Inotify watcher started for %zu files", filesToWatch.size());
    int fd = inotify_init();
    if (fd < 0) {
        LOGE("inotify_init failed");
        return;
    }

    for (const std::string& file : filesToWatch) {
        int wd = inotify_add_watch(fd, file.c_str(), IN_OPEN | IN_ACCESS);
        if (wd < 0) {
            LOGE("inotify_add_watch failed for %s", file.c_str());
        } else {
            LOGI("Watching %s", file.c_str());
        }
    }

    char buffer[4096] __attribute__ ((aligned(__alignof__(struct inotify_event))));
    while (true) {
        ssize_t len = read(fd, buffer, sizeof(buffer));
        if (len < 0) {
            LOGE("inotify read failed");
            break;
        }
        
        LOGE("INOTIFY TRIGGERED! A file was accessed.");

        // A file was opened!
        // We trigger the ban callback.
        JNIEnv *env;
        int getEnvStat = gJvm->GetEnv((void **)&env, JNI_VERSION_1_6);
        bool attached = false;
        if (getEnvStat == JNI_EDETACHED) {
            if (gJvm->AttachCurrentThread(&env, nullptr) != 0) {
                LOGE("Failed to attach");
                continue;
            }
            attached = true;
        }
        
        if (gAntiTamperClass != nullptr && gBanMethod != nullptr) {
            jstring reason = env->NewStringUTF("FILE_READ_DETECTED");
            env->CallStaticVoidMethod(gAntiTamperClass, gBanMethod, reason);
            env->DeleteLocalRef(reason);
            LOGI("Called executePermanentBanNative successfully");
        } else {
            LOGE("Global class or method is null!");
        }

        if (attached) {
            gJvm->DetachCurrentThread();
        }
    }
    close(fd);
}

extern "C" JNIEXPORT void JNICALL
Java_eu_kodanetwork_mchost_security_PraetorSecurity_startInotifyWatcher(
        JNIEnv* env,
        jclass clazz,
        jobjectArray filesToWatchArray) {
        
    // Cache the class and method ID using the application classloader
    if (gAntiTamperClass == nullptr) {
        jclass localClass = env->FindClass("eu/kodanetwork/mchost/security/AntiTamperSystem");
        if (localClass != nullptr) {
            gAntiTamperClass = (jclass) env->NewGlobalRef(localClass);
            gBanMethod = env->GetStaticMethodID(gAntiTamperClass, "executePermanentBanNative", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(localClass);
        }
    }

    if (filesToWatchArray == nullptr) return;
    
    std::vector<std::string> filesToWatch;
    int count = env->GetArrayLength(filesToWatchArray);
    for (int i = 0; i < count; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(filesToWatchArray, i);
        const char *str = env->GetStringUTFChars(jstr, 0);
        filesToWatch.push_back(std::string(str));
        env->ReleaseStringUTFChars(jstr, str);
        env->DeleteLocalRef(jstr);
    }
    
    std::thread watcher(inotifyWatcherThread, filesToWatch);
    watcher.detach();
    
    std::thread antiDebugger(checkTracerPidThread);
    antiDebugger.detach();
}

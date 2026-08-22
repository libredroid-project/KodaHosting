#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <vector>
#include <sys/stat.h>
#include <dirent.h>

#define LOG_TAG "EmbeddedJVM_C"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include <fstream>
#include <sstream>

typedef jint (*JNICreateJavaVM_t)(JavaVM **, void **, void *);

static JavaVM* g_jvm = nullptr;

static void jvm_exit_hook(jint code) {
    LOGI("JVM intercepted exit(%d)! Exiting process cleanly.", code);
    _exit(code);
}

extern "C" JNIEXPORT jint JNICALL
Java_eu_kodanetwork_mchost_service_IsolatedJvmService_startEmbeddedJvmNative(
        JNIEnv* env,
        jclass clazz,
        jstring libJvmPath,
        jstring jarPath,
        jint ramMb,
        jstring mainClassStr,
        jstring workDirStr) {

    const char *jvm_path = env->GetStringUTFChars(libJvmPath, 0);
    const char *jar_path = env->GetStringUTFChars(jarPath, 0);
    const char *main_class = env->GetStringUTFChars(mainClassStr, 0);
    const char *work_dir = env->GetStringUTFChars(workDirStr, 0);

    std::string stdout_log = std::string(work_dir) + "/server.log";
    std::string stderr_log = std::string(work_dir) + "/server.log";
    freopen(stdout_log.c_str(), "w", stdout);
    freopen(stderr_log.c_str(), "a", stderr);

    LOGI("Attempting to load JVM from: %s", jvm_path);
    LOGI("Setting working directory to: %s", work_dir);
    chdir(work_dir);
    
    // Create tmp directory
    std::string tmp_dir = std::string(work_dir) + "/tmp";
    mkdir(tmp_dir.c_str(), 0777);
    setenv("TMPDIR", tmp_dir.c_str(), 1);

    JNIEnv* vmEnv = nullptr;

    if (g_jvm == nullptr) {
        std::string jvmStr(jvm_path);
        size_t pos = jvmStr.rfind("/server/libjvm.so");
        if (pos == std::string::npos) pos = jvmStr.rfind("/client/libjvm.so");
        
        void* handle = nullptr;
        if (pos != std::string::npos) {
            std::string lib_dir = jvmStr.substr(0, pos) + "/";
            LOGI("Preloading JVM dependencies from: %s", lib_dir.c_str());
            dlopen((lib_dir + "libc++_shared.so").c_str(), RTLD_NOW | RTLD_GLOBAL);
            dlopen((lib_dir + "jli/libjli.so").c_str(), RTLD_NOW | RTLD_GLOBAL);
            
            LOGI("Loading libjvm.so via JNI...");
            handle = dlopen(jvm_path, RTLD_NOW | RTLD_GLOBAL);
            if (!handle) {
                LOGE("dlopen failed for libjvm.so: %s", dlerror());
                env->ReleaseStringUTFChars(libJvmPath, jvm_path);
                env->ReleaseStringUTFChars(jarPath, jar_path);
                return -1;
            }

            // Loop to resolve all other JRE libraries
            bool changed = true;
            std::vector<std::string> loaded;
            while (changed) {
                changed = false;
                DIR* dir = opendir(lib_dir.c_str());
                if (dir) {
                    struct dirent* entry;
                    while ((entry = readdir(dir)) != nullptr) {
                        std::string name = entry->d_name;
                        if (name.find(".so") != std::string::npos && name != "libjvm.so" && name != "libc++_shared.so") {
                            // Check if already loaded
                            bool already_loaded = false;
                            for (const auto& l : loaded) {
                                if (l == name) { already_loaded = true; break; }
                            }
                            if (!already_loaded) {
                                void* lib = dlopen((lib_dir + name).c_str(), RTLD_NOW | RTLD_GLOBAL);
                                if (lib) {
                                    loaded.push_back(name);
                                    changed = true;
                                    LOGI("Successfully preloaded: %s", name.c_str());
                                }
                            }
                        }
                    }
                    closedir(dir);
                }
            }
        } else {
            LOGI("Loading libjvm.so via JNI...");
            handle = dlopen(jvm_path, RTLD_NOW | RTLD_GLOBAL);
            if (!handle) {
                LOGE("dlopen failed for libjvm.so: %s", dlerror());
                env->ReleaseStringUTFChars(libJvmPath, jvm_path);
                env->ReleaseStringUTFChars(jarPath, jar_path);
                return -1;
            }
        }

        JNICreateJavaVM_t createVM = (JNICreateJavaVM_t) dlsym(handle, "JNI_CreateJavaVM");
        if (!createVM) {
            LOGE("dlsym JNI_CreateJavaVM failed: %s", dlerror());
            dlclose(handle);
            env->ReleaseStringUTFChars(libJvmPath, jvm_path);
            env->ReleaseStringUTFChars(jarPath, jar_path);
            return -2;
        }

        char ramStr[32];
        snprintf(ramStr, sizeof(ramStr), "-Xmx%dm", ramMb);

        char classPath[512];
        snprintf(classPath, sizeof(classPath), "-Djava.class.path=%s", jar_path);

        char libraryPath[512];
        std::string lib_dir = jvmStr.substr(0, jvmStr.rfind("/server/libjvm.so") != std::string::npos ? jvmStr.rfind("/server/libjvm.so") : jvmStr.rfind("/client/libjvm.so"));
        snprintf(libraryPath, sizeof(libraryPath), "-Djava.library.path=%s", lib_dir.c_str());

        char bootLibraryPath[512];
        snprintf(bootLibraryPath, sizeof(bootLibraryPath), "-Dsun.boot.library.path=%s", lib_dir.c_str());

        char paperIgnoreJava[64] = "-DPaper.IgnoreJavaVersion=true";
        
        char tmpDirArg[512];
        snprintf(tmpDirArg, sizeof(tmpDirArg), "-Djava.io.tmpdir=%s", tmp_dir.c_str());

        char jnaTmpDirArg[512];
        snprintf(jnaTmpDirArg, sizeof(jnaTmpDirArg), "-Djna.tmpdir=%s", tmp_dir.c_str());

        char vendorArg[64] = "-Djava.vendor=The Android Project";
        char noNativeNetty[64] = "-Dio.netty.transport.noNative=true";
        char noNativeNettyGeyser[64] = "-Dorg.cloudburstmc.netty.transport.noNative=true";
        char ipv4Stack[64] = "-Djava.net.preferIPv4Stack=true";
        char noJline[64] = "-Dorg.jline.terminal.jansi=false";
        
        char jnaNosys[64] = "-Djna.nosys=true";
        char jnaNoUnpack[64] = "-Djna.nounpack=true";
        char jlineFalse[64] = "-Dterminal.jline=false";
        char ansiTrue[64] = "-Dterminal.ansi=true";

        JavaVMOption options[64];
        int optCount = 0;
        options[optCount++].optionString = ramStr;
        options[optCount++].optionString = classPath;
        options[optCount++].optionString = libraryPath;
        options[optCount++].optionString = bootLibraryPath;
        options[optCount++].optionString = paperIgnoreJava;
        options[optCount++].optionString = tmpDirArg;
        options[optCount++].optionString = jnaTmpDirArg;
        options[optCount++].optionString = vendorArg;
        options[optCount++].optionString = noNativeNetty;
        options[optCount++].optionString = noNativeNettyGeyser;
        options[optCount++].optionString = ipv4Stack;
        options[optCount++].optionString = noJline;
        
        options[optCount++].optionString = jnaNosys;
        options[optCount++].optionString = jnaNoUnpack;
        options[optCount++].optionString = jlineFalse;
        options[optCount++].optionString = ansiTrue;
        options[optCount++].optionString = (char*)"-Xss2M";
        
        options[optCount].optionString = (char*)"exit";
        options[optCount].extraInfo = (void*) jvm_exit_hook;
        optCount++;
        
        // Aikar's Flags for performance
        options[optCount++].optionString = (char*)"-server";
        options[optCount++].optionString = (char*)"-XX:+UseG1GC";
        options[optCount++].optionString = (char*)"-XX:+ParallelRefProcEnabled";
        options[optCount++].optionString = (char*)"-XX:MaxGCPauseMillis=100";
        options[optCount++].optionString = (char*)"-XX:+UnlockExperimentalVMOptions";
        options[optCount++].optionString = (char*)"-XX:+DisableExplicitGC";
        options[optCount++].optionString = (char*)"-XX:G1NewSizePercent=30";
        options[optCount++].optionString = (char*)"-XX:G1MaxNewSizePercent=40";
        options[optCount++].optionString = (char*)"-XX:G1HeapRegionSize=8M";
        options[optCount++].optionString = (char*)"-XX:G1ReservePercent=20";
        options[optCount++].optionString = (char*)"-XX:G1HeapWastePercent=5";
        options[optCount++].optionString = (char*)"-XX:G1MixedGCCountTarget=4";
        options[optCount++].optionString = (char*)"-XX:InitiatingHeapOccupancyPercent=15";
        options[optCount++].optionString = (char*)"-XX:G1MixedGCLiveThresholdPercent=90";
        options[optCount++].optionString = (char*)"-XX:G1RSetUpdatingPauseTimePercent=5";
        options[optCount++].optionString = (char*)"-XX:SurvivorRatio=32";
        options[optCount++].optionString = (char*)"-XX:+PerfDisableSharedMem";
        options[optCount++].optionString = (char*)"-XX:MaxTenuringThreshold=1";
        options[optCount++].optionString = (char*)"-Dusing.aikars.flags=https://mcflags.emc.gs";
        options[optCount++].optionString = (char*)"-Djava.awt.headless=true";

        // Read extra JVM arguments from koda_jvm_args.txt
        std::vector<std::string> extraJvmArgs;
        std::string jvmArgsPath = std::string(work_dir) + "/koda_jvm_args.txt";
        std::ifstream jvmArgsFile(jvmArgsPath);
        if (jvmArgsFile.is_open()) {
            std::string line;
            while (std::getline(jvmArgsFile, line)) {
                if (!line.empty() && line[0] != '#') {
                    // Remove carriage return if present
                    if (!line.empty() && line.back() == '\r') line.pop_back();
                    extraJvmArgs.push_back(line);
                }
            }
            jvmArgsFile.close();
        }

        // Add them to options array
        for (const auto& arg : extraJvmArgs) {
            if (optCount < 128) {
                options[optCount++].optionString = (char*)arg.c_str();
            }
        }

        JavaVMInitArgs vm_args;
        vm_args.version = JNI_VERSION_1_6;
        vm_args.nOptions = optCount;
        vm_args.options = options;
        vm_args.ignoreUnrecognized = JNI_TRUE;

        LOGI("Creating Java VM...");
        jint res = createVM(&g_jvm, (void**)&vmEnv, &vm_args);
        
        if (res != JNI_OK || !vmEnv) {
            LOGE("JNI_CreateJavaVM failed with error code: %d", res);
            dlclose(handle);
            env->ReleaseStringUTFChars(libJvmPath, jvm_path);
            env->ReleaseStringUTFChars(jarPath, jar_path);
            return res;
        }
    }

    LOGI("JVM ready! Looking for Main class: %s", main_class);

    jclass mainClass = vmEnv->FindClass(main_class);
    if (!mainClass) {
        LOGE("Could not find class: %s", main_class);
        if (vmEnv->ExceptionCheck()) {
            vmEnv->ExceptionDescribe();
            vmEnv->ExceptionClear();
        }
        env->ReleaseStringUTFChars(libJvmPath, jvm_path);
        env->ReleaseStringUTFChars(jarPath, jar_path);
        env->ReleaseStringUTFChars(mainClassStr, main_class);
        return -3;
    }

    jmethodID mainMethod = vmEnv->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("Could not find main method!");
        if (vmEnv->ExceptionCheck()) {
            vmEnv->ExceptionDescribe();
            vmEnv->ExceptionClear();
        }
        env->ReleaseStringUTFChars(libJvmPath, jvm_path);
        env->ReleaseStringUTFChars(jarPath, jar_path);
        return -4;
    }

    LOGI("Calling main method. The embedded server should now start in this thread!");
    bool isFabric = strstr(main_class, "fabric") != nullptr || strstr(main_class, "Fabric") != nullptr;
    jclass stringClass = vmEnv->FindClass("java/lang/String");
    jobjectArray args;
    
    // Read dynamic program args if available
    std::vector<std::string> extraProgArgs;
    std::string progArgsPath = std::string(work_dir) + "/koda_program_args.txt";
    std::ifstream progArgsFile(progArgsPath);
    if (progArgsFile.is_open()) {
        std::string line;
        while (std::getline(progArgsFile, line)) {
            if (!line.empty()) {
                if (!line.empty() && line.back() == '\r') line.pop_back();
                extraProgArgs.push_back(line);
            }
        }
        progArgsFile.close();
    }
    
    if (!extraProgArgs.empty()) {
        args = vmEnv->NewObjectArray(extraProgArgs.size(), stringClass, nullptr);
        for (size_t i = 0; i < extraProgArgs.size(); i++) {
            jstring arg = vmEnv->NewStringUTF(extraProgArgs[i].c_str());
            vmEnv->SetObjectArrayElement(args, i, arg);
        }
    } else if (isFabric) {
        args = vmEnv->NewObjectArray(1, stringClass, nullptr);
        jstring arg1 = vmEnv->NewStringUTF("--nogui");
        vmEnv->SetObjectArrayElement(args, 0, arg1);
    } else {
        args = vmEnv->NewObjectArray(3, stringClass, nullptr);
        jstring arg1 = vmEnv->NewStringUTF("--nogui");
        jstring arg2 = vmEnv->NewStringUTF("--add-plugin");
        jstring arg3 = vmEnv->NewStringUTF(".sys/koda_core.jar");
        vmEnv->SetObjectArrayElement(args, 0, arg1);
        vmEnv->SetObjectArrayElement(args, 1, arg2);
        vmEnv->SetObjectArrayElement(args, 2, arg3);
    }
    
    vmEnv->CallStaticVoidMethod(mainClass, mainMethod, args);

    LOGI("Main method returned. Checking for Server thread...");
    if (vmEnv->ExceptionCheck()) {
        vmEnv->ExceptionDescribe(); // Print the exception to logcat/stderr!
        vmEnv->ExceptionClear();
    }

    // Wait until "Server thread" is gone
    jclass threadClass = vmEnv->FindClass("java/lang/Thread");
    jmethodID getAllStackTraces = vmEnv->GetStaticMethodID(threadClass, "getAllStackTraces", "()Ljava/util/Map;");
    jclass mapClass = vmEnv->FindClass("java/util/Map");
    jmethodID keySet = vmEnv->GetMethodID(mapClass, "keySet", "()Ljava/util/Set;");
    jclass setClass = vmEnv->FindClass("java/util/Set");
    jmethodID toArray = vmEnv->GetMethodID(setClass, "toArray", "()[Ljava/lang/Object;");
    jmethodID getName = vmEnv->GetMethodID(threadClass, "getName", "()Ljava/lang/String;");

    bool serverRunning = true;
    while (serverRunning) {
        sleep(2);
        serverRunning = false;
        
        jobject map = vmEnv->CallStaticObjectMethod(threadClass, getAllStackTraces);
        if (map) {
            jobject set = vmEnv->CallObjectMethod(map, keySet);
            if (set) {
                jobjectArray threads = (jobjectArray) vmEnv->CallObjectMethod(set, toArray);
                if (threads) {
                    jsize count = vmEnv->GetArrayLength(threads);
                    for (jsize i = 0; i < count; i++) {
                        jobject threadObj = vmEnv->GetObjectArrayElement(threads, i);
                        if (threadObj) {
                            jstring nameObj = (jstring) vmEnv->CallObjectMethod(threadObj, getName);
                            if (nameObj) {
                                const char* name = vmEnv->GetStringUTFChars(nameObj, nullptr);
                                if (strstr(name, "Server thread") != nullptr || strstr(name, "ServerMain") != nullptr) {
                                    serverRunning = true;
                                }
                                vmEnv->ReleaseStringUTFChars(nameObj, name);
                                vmEnv->DeleteLocalRef(nameObj);
                            }
                            vmEnv->DeleteLocalRef(threadObj);
                        }
                        if (serverRunning) break;
                    }
                    vmEnv->DeleteLocalRef(threads);
                }
                vmEnv->DeleteLocalRef(set);
            }
            vmEnv->DeleteLocalRef(map);
        }
        
        if (!serverRunning) {
            break;
        }
    }

    LOGI("JVM kept alive for future server restarts.");

    env->ReleaseStringUTFChars(libJvmPath, jvm_path);
    env->ReleaseStringUTFChars(jarPath, jar_path);
    env->ReleaseStringUTFChars(mainClassStr, main_class);
    env->ReleaseStringUTFChars(workDirStr, work_dir);

    return 0;
}

#include <jni.h>
#include <string>
#include <vector>
#include "node.h"
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdio>

static int outPipe[2];
static int errPipe[2];

static void *logStdout(void *) {
    char buf[2048];
    ssize_t n;
    while ((n = read(outPipe[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = 0;
        __android_log_write(ANDROID_LOG_INFO, "NODEJS-MOBILE", buf);
    }
    return nullptr;
}

static void *logStderr(void *) {
    char buf[2048];
    ssize_t n;
    while ((n = read(errPipe[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = 0;
        __android_log_write(ANDROID_LOG_ERROR, "NODEJS-MOBILE", buf);
    }
    return nullptr;
}

static void redirectLogs() {
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    pipe(outPipe);
    dup2(outPipe[1], STDOUT_FILENO);
    pipe(errPipe);
    dup2(errPipe[1], STDERR_FILENO);
    pthread_t t1, t2;
    pthread_create(&t1, nullptr, logStdout, nullptr);
    pthread_detach(t1);
    pthread_create(&t2, nullptr, logStderr, nullptr);
    pthread_detach(t2);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_kyant_backdrop_catalog_NodeProcess_nativeStart(
        JNIEnv *env, jobject /* this */, jobjectArray arguments) {
    const jsize count = env->GetArrayLength(arguments);
    std::vector<std::string> storage;
    storage.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        auto value = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *chars = env->GetStringUTFChars(value, nullptr);
        storage.emplace_back(chars == nullptr ? "" : chars);
        if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }
    redirectLogs();
    std::vector<char *> argv;
    argv.reserve(count);
    for (auto &item : storage) argv.push_back(item.data());
    return node::Start(static_cast<int>(argv.size()), argv.data());
}

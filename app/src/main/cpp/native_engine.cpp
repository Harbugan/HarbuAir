// HarbuAir native engine: JNI bridge around airplay2-sender-cpp (Apache-2.0).
//
// Threading model: RaopSender is NOT thread-safe. Every sender call happens on
// ONE worker thread ("loop thread"). Other threads (UI, audio source) only
//   (a) post commands into a mutex-protected queue, or
//   (b) push PCM into the lock-free SPSC ring (single producer = audio source).
// Callbacks into Kotlin are made from the loop thread only.
//
// PCM contract: interleaved signed 16-bit stereo, at the sample rate given to
// nativeStart (the library resamples linearly to 44.1 kHz if different).
// There is no mono downmix anywhere in this file.
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "raop_loop.h"
#include "raop_sender.h"
#include "ring_buffer.h"

using namespace fxchain;

namespace {

constexpr const char* kTag = "HarbuAirNative";
JavaVM* g_vm = nullptr;

// Event codes shared with Kotlin (engine/NativeEngine.kt). Keep in sync.
enum EventType : jint {
    EV_LAUNCHED_OK = 0,
    EV_LAUNCH_FAILED = 1,
    EV_CLOSED_BY_RECEIVER = 2,
    EV_PIN_REQUIRED = 3,
    EV_CREDENTIALS = 4,   // payload is pairing material: persist, NEVER log
    EV_LOG_INFO = 5,
    EV_LOG_WARN = 6,
};

struct Engine {
    RaopLoop loop;
    RingBuffer<int16_t> ring{1u << 18};  // 262144 samples = 131072 stereo frames ~ 3.0 s @ 44.1 kHz
    std::unique_ptr<RaopSender> sender;

    std::thread worker;
    std::mutex mu;
    std::deque<std::function<void()>> cmds;
    std::atomic<bool> quit{false};

    jobject callback = nullptr;  // global ref to NativeEngine.Listener
    jmethodID onEvent = nullptr;

    // Loop-thread only:
    JNIEnv* env = nullptr;
};

void emit(Engine* e, EventType t, const std::string& a = {}, const std::string& b = {}) {
    if (!e->env || !e->callback) return;
    jstring ja = e->env->NewStringUTF(a.c_str());
    jstring jb = e->env->NewStringUTF(b.c_str());
    e->env->CallVoidMethod(e->callback, e->onEvent, static_cast<jint>(t), ja, jb);
    if (e->env->ExceptionCheck()) { e->env->ExceptionClear(); }
    e->env->DeleteLocalRef(ja);
    e->env->DeleteLocalRef(jb);
}

void post(Engine* e, std::function<void()> fn) {
    std::lock_guard<std::mutex> g(e->mu);
    e->cmds.push_back(std::move(fn));
}

// Give the loop a short time to flush TEARDOWN onto the wire.
void pumpFor(Engine* e, int ms) {
    auto end = std::chrono::steady_clock::now() + std::chrono::milliseconds(ms);
    while (std::chrono::steady_clock::now() < end) e->loop.pump(*e->sender, std::chrono::milliseconds(20));
}

void workerMain(Engine* e) {
    if (g_vm->AttachCurrentThread(&e->env, nullptr) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AttachCurrentThread failed");
        return;
    }
    while (!e->quit.load()) {
        for (;;) {
            std::function<void()> fn;
            {
                std::lock_guard<std::mutex> g(e->mu);
                if (e->cmds.empty()) break;
                fn = std::move(e->cmds.front());
                e->cmds.pop_front();
            }
            fn();
        }
        e->loop.pump(*e->sender, std::chrono::milliseconds(20));
    }
    e->env = nullptr;
    g_vm->DetachCurrentThread();
}

Engine* fromHandle(jlong h) { return reinterpret_cast<Engine*>(h); }

std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeCreate(JNIEnv* env, jobject, jobject listener) {
    auto* e = new Engine();
    e->callback = env->NewGlobalRef(listener);
    jclass cls = env->GetObjectClass(listener);
    e->onEvent = env->GetMethodID(cls, "onNativeEvent", "(ILjava/lang/String;Ljava/lang/String;)V");

    RaopEvents ev;
    ev.launched = [e](bool ok, const std::string& err) {
        emit(e, ok ? EV_LAUNCHED_OK : EV_LAUNCH_FAILED, err);
    };
    ev.closed = [e]() { emit(e, EV_CLOSED_BY_RECEIVER); };
    ev.pinRequired = [e](const std::string& name) { emit(e, EV_PIN_REQUIRED, name); };
    ev.credentialsObtained = [e](const std::string& id, const std::string& json) {
        emit(e, EV_CREDENTIALS, id, json);
    };
    RaopLogSink log = [e](RaopLogLevel lvl, const std::string& line) {
        emit(e, lvl == RaopLogLevel::Warn ? EV_LOG_WARN : EV_LOG_INFO, line);
    };
    e->sender = std::make_unique<RaopSender>(e->loop, std::move(ev), std::move(log));
    e->sender->attachRing(&e->ring);
    e->worker = std::thread(workerMain, e);
    return reinterpret_cast<jlong>(e);
}

// authMode: 0=None(AirPlay1) 4=HapTransient 5=HapPin (matches RaopDeviceInfo::Auth ordinals)
JNIEXPORT void JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeStart(JNIEnv* env, jobject, jlong h, jstring host, jint port,
                                                 jstring name, jint authMode, jboolean airplay2,
                                                 jstring receiverId, jstring credsJson, jint sampleRate,
                                                 jboolean strict,
                                                 jstring senderName, jstring senderMac) {
    Engine* e = fromHandle(h);
    std::string sHost = str(env, host), sName = str(env, name), sId = str(env, receiverId);
    std::string sCreds = str(env, credsJson), sSender = str(env, senderName), sMac = str(env, senderMac);
    post(e, [=] {
        // Discard stale PCM from a previous session (consumer side == this thread).
        std::vector<int16_t> sink(e->ring.availableRead());
        if (!sink.empty()) e->ring.tryPop(std::span<int16_t>(sink));
        RaopIdentity id;
        id.name = sSender;
        id.deviceId = sMac;  // random per-install locally-administered MAC, not the hardware MAC
        e->sender->setIdentity(id);
        e->sender->setInputFormat(static_cast<uint32_t>(sampleRate));
        e->sender->setStrictReceiverAuth(strict);
        e->sender->setAuth(static_cast<RaopDeviceInfo::Auth>(authMode), airplay2, sId, sCreds, "");
        e->sender->start(sHost, static_cast<uint16_t>(port), sName);
    });
}

JNIEXPORT void JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeStop(JNIEnv*, jobject, jlong h) {
    Engine* e = fromHandle(h);
    post(e, [e] {
        e->sender->stop();  // TEARDOWN + close; silent (no `closed` event)
        pumpFor(e, 300);
    });
}

JNIEXPORT void JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeSubmitPin(JNIEnv* env, jobject, jlong h, jstring pin) {
    Engine* e = fromHandle(h);
    std::string p = str(env, pin);
    post(e, [e, p] { e->sender->submitPin(p); });
}

JNIEXPORT void JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeSetVolume(JNIEnv*, jobject, jlong h, jdouble pct) {
    Engine* e = fromHandle(h);
    post(e, [e, pct] { e->sender->setVolume(pct); });
}

// Returns number of stereo FRAMES accepted (0 if the ring is full). All-or-nothing.
JNIEXPORT jint JNICALL
Java_dk_harbuair_engine_NativeEngine_nativePushPcm(JNIEnv* env, jobject, jlong h, jshortArray pcm, jint frames) {
    Engine* e = fromHandle(h);
    const jsize n = static_cast<jsize>(frames) * 2;
    if (frames <= 0 || env->GetArrayLength(pcm) < n) return 0;
    jshort* data = env->GetShortArrayElements(pcm, nullptr);
    bool ok = e->ring.tryPush(std::span<const int16_t>(reinterpret_cast<const int16_t*>(data), n));
    env->ReleaseShortArrayElements(pcm, data, JNI_ABORT);
    return ok ? frames : 0;
}

JNIEXPORT jint JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeRingFreeFrames(JNIEnv*, jobject, jlong h) {
    return static_cast<jint>(fromHandle(h)->ring.availableWrite() / 2);
}

JNIEXPORT void JNICALL
Java_dk_harbuair_engine_NativeEngine_nativeDestroy(JNIEnv* env, jobject, jlong h) {
    Engine* e = fromHandle(h);
    post(e, [e] {
        e->sender->stop();
        pumpFor(e, 300);
        e->quit.store(true);
    });
    if (e->worker.joinable()) e->worker.join();
    e->sender.reset();
    env->DeleteGlobalRef(e->callback);
    delete e;
}

}  // extern "C"

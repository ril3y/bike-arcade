/**
 * JNI bridge between Kotlin (LibretroBridge.kt) and the libretro C API.
 *
 * This file dynamically loads a libretro core .so via dlopen, resolves all
 * retro_* symbols with dlsym, and implements the native methods declared in
 * com.bikearcade.emulator.LibretroBridge.
 *
 * Video frames, audio samples, and input queries are forwarded to the Kotlin
 * side through JNI callback invocations.
 */

#include <jni.h>
#include <dlfcn.h>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <android/log.h>

#define TAG "RetroJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── libretro.h types (inlined to avoid header dependency) ───────────────

#define RETRO_API_VERSION 1

#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT     10
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY 9
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY   31
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE    27
#define RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS 31
#define RETRO_ENVIRONMENT_GET_VARIABLE         15
#define RETRO_ENVIRONMENT_SET_VARIABLES        16
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE  17

enum retro_pixel_format {
    RETRO_PIXEL_FORMAT_0RGB1555 = 0,
    RETRO_PIXEL_FORMAT_XRGB8888 = 1,
    RETRO_PIXEL_FORMAT_RGB565 = 2,
    RETRO_PIXEL_FORMAT_UNKNOWN = INT32_MAX
};

struct retro_system_info {
    const char *library_name;
    const char *library_version;
    const char *valid_extensions;
    bool        need_fullpath;
    bool        block_extract;
};

struct retro_system_av_info {
    struct {
        unsigned base_width;
        unsigned base_height;
        unsigned max_width;
        unsigned max_height;
        float    aspect_ratio;
    } geometry;
    struct {
        double fps;
        double sample_rate;
    } timing;
};

struct retro_game_info {
    const char *path;
    const void *data;
    size_t      size;
    const char *meta;
};

struct retro_variable {
    const char *key;
    const char *value;
};

// ── Function pointer typedefs ───────────────────────────────────────────

typedef void (*retro_set_environment_t)(bool (*)(unsigned, void*));
typedef void (*retro_set_video_refresh_t)(void (*)(const void*, unsigned, unsigned, size_t));
typedef void (*retro_set_audio_sample_t)(void (*)(int16_t, int16_t));
typedef void (*retro_set_audio_sample_batch_t)(size_t (*)(const int16_t*, size_t));
typedef void (*retro_set_input_poll_t)(void (*)());
typedef void (*retro_set_input_state_t)(int16_t (*)(unsigned, unsigned, unsigned, unsigned));
typedef void (*retro_init_t)(void);
typedef void (*retro_deinit_t)(void);
typedef unsigned (*retro_api_version_t)(void);
typedef void (*retro_get_system_info_t)(struct retro_system_info*);
typedef void (*retro_get_system_av_info_t)(struct retro_system_av_info*);
typedef bool (*retro_load_game_t)(const struct retro_game_info*);
typedef void (*retro_unload_game_t)(void);
typedef void (*retro_run_t)(void);
typedef void (*retro_reset_t)(void);

// ── Global state ────────────────────────────────────────────────────────

static void *g_core_handle = nullptr;

static retro_set_environment_t       p_retro_set_environment       = nullptr;
static retro_set_video_refresh_t     p_retro_set_video_refresh     = nullptr;
static retro_set_audio_sample_t      p_retro_set_audio_sample      = nullptr;
static retro_set_audio_sample_batch_t p_retro_set_audio_sample_batch = nullptr;
static retro_set_input_poll_t        p_retro_set_input_poll        = nullptr;
static retro_set_input_state_t       p_retro_set_input_state       = nullptr;
static retro_init_t                  p_retro_init                  = nullptr;
static retro_deinit_t                p_retro_deinit                = nullptr;
static retro_api_version_t           p_retro_api_version           = nullptr;
static retro_get_system_info_t       p_retro_get_system_info       = nullptr;
static retro_get_system_av_info_t    p_retro_get_system_av_info    = nullptr;
static retro_load_game_t             p_retro_load_game             = nullptr;
static retro_unload_game_t           p_retro_unload_game           = nullptr;
static retro_run_t                   p_retro_run                   = nullptr;
static retro_reset_t                 p_retro_reset                 = nullptr;

static JavaVM       *g_jvm = nullptr;
static jobject       g_bridge_obj = nullptr;   // Global ref to LibretroBridge companion
static jclass        g_bridge_class = nullptr;  // Global ref to LibretroBridge class

// Cached method IDs
static jmethodID g_mid_onVideoRefresh    = nullptr;
static jmethodID g_mid_onAudioSample     = nullptr;
static jmethodID g_mid_onAudioSampleBatch = nullptr;
static jmethodID g_mid_onInputPoll       = nullptr;
static jmethodID g_mid_onInputState      = nullptr;

static retro_pixel_format g_pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;

// Audio sample buffering — accumulates single samples into batches
static int16_t *g_audio_buf = nullptr;
static size_t g_audio_buf_capacity = 0;
static size_t g_audio_buf_pos = 0;  // position in samples (not frames)

// Pre-allocated JNI short array to avoid GC pressure from per-frame allocation
static jshortArray g_audio_jarray = nullptr;
static jint g_audio_jarray_size = 0;

// Pre-allocated DirectByteBuffer for video frames (avoids per-frame JNI object creation)
static jobject g_video_bytebuffer = nullptr;
static size_t g_video_bytebuffer_size = 0;

// Pre-allocated ARGB8888 conversion buffer for non-XRGB8888 formats
static uint32_t *g_convert_buf = nullptr;
static size_t    g_convert_buf_size = 0;

// RGB565 -> ARGB8888 lookup tables (5-bit R, 6-bit G, 5-bit B)
static uint8_t g_r5_to_8[32];
static uint8_t g_g6_to_8[64];
static uint8_t g_b5_to_8[32];
static bool g_lut_initialized = false;

static void init_rgb_lut() {
    if (g_lut_initialized) return;
    for (int i = 0; i < 32; i++) g_r5_to_8[i] = (uint8_t)(i * 255 / 31);
    for (int i = 0; i < 64; i++) g_g6_to_8[i] = (uint8_t)(i * 255 / 63);
    for (int i = 0; i < 32; i++) g_b5_to_8[i] = (uint8_t)(i * 255 / 31);
    g_lut_initialized = true;
}

// System/save directory paths
static char g_system_dir[512] = "/storage/emulated/0/BikeArcade/system";
static char g_save_dir[512]   = "/storage/emulated/0/BikeArcade/saves";

// ── JNI OnLoad ──────────────────────────────────────────────────────────

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    g_jvm = vm;

    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: Failed to get JNIEnv");
        return JNI_ERR;
    }

    // Cache the LibretroBridge class and method IDs
    jclass localClass = env->FindClass("com/bikearcade/emulator/LibretroBridge");
    if (!localClass) {
        LOGE("JNI_OnLoad: Cannot find LibretroBridge class");
        return JNI_ERR;
    }
    g_bridge_class = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    env->DeleteLocalRef(localClass);

    // Cache static method IDs
    g_mid_onVideoRefresh = env->GetStaticMethodID(
        g_bridge_class, "onVideoRefresh", "(Ljava/nio/ByteBuffer;IIII)V");
    g_mid_onAudioSample = env->GetStaticMethodID(
        g_bridge_class, "onAudioSample", "(SS)V");
    g_mid_onAudioSampleBatch = env->GetStaticMethodID(
        g_bridge_class, "onAudioSampleBatch", "([SI)V");
    g_mid_onInputPoll = env->GetStaticMethodID(
        g_bridge_class, "onInputPoll", "()V");
    g_mid_onInputState = env->GetStaticMethodID(
        g_bridge_class, "onInputState", "(IIII)I");

    if (!g_mid_onVideoRefresh || !g_mid_onAudioSample ||
        !g_mid_onAudioSampleBatch || !g_mid_onInputPoll || !g_mid_onInputState) {
        LOGE("JNI_OnLoad: Failed to resolve one or more callback method IDs");
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad: retro_jni initialized");
    return JNI_VERSION_1_6;
}

// ── Helper: get JNIEnv for the current thread ───────────────────────────

static JNIEnv* getEnv() {
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

// ── dlsym helper ────────────────────────────────────────────────────────

#define LOAD_SYM(handle, name) \
    p_##name = reinterpret_cast<name##_t>(dlsym(handle, #name)); \
    if (!p_##name) { LOGW("dlsym: " #name " not found"); }

// ── libretro callbacks (called by the core) ─────────────────────────────

static bool cb_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            auto *fmt = static_cast<retro_pixel_format*>(data);
            g_pixel_format = *fmt;
            LOGI("Core requested pixel format: %d", static_cast<int>(*fmt));
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            auto **dir = static_cast<const char**>(data);
            *dir = g_system_dir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            auto **dir = static_cast<const char**>(data);
            *dir = g_save_dir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto *var = static_cast<retro_variable*>(data);
            var->value = nullptr;
            return false;
        }
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            return false;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            // We do not provide a log interface; core will use its fallback
            return false;
        default:
            return false;
    }
}

static void cb_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;

    JNIEnv *env = getEnv();
    if (!env) return;

    // Clear any pending exceptions
    if (env->ExceptionCheck()) { env->ExceptionClear(); }

    size_t total_pixels = static_cast<size_t>(width) * height;

    if (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
        // XRGB8888 (0x00RRGGBB): convert to ABGR for copyPixelsFromBuffer
        if (g_convert_buf_size < total_pixels) {
            free(g_convert_buf);
            g_convert_buf = static_cast<uint32_t*>(malloc(total_pixels * 4));
            g_convert_buf_size = total_pixels;
        }
        if (!g_convert_buf) return;

        const uint8_t *src = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; y++) {
            const uint32_t *row = reinterpret_cast<const uint32_t*>(src + y * pitch);
            uint32_t *out = g_convert_buf + y * width;
            for (unsigned x = 0; x < width; x++) {
                uint32_t px = row[x];
                uint8_t r = (px >> 16) & 0xFF;
                uint8_t g = (px >> 8) & 0xFF;
                uint8_t b = px & 0xFF;
                out[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }

    } else {
        // RGB565 or 0RGB1555: convert to XRGB8888 in native code for performance
        init_rgb_lut();

        // Ensure conversion buffer is large enough
        if (g_convert_buf_size < total_pixels) {
            free(g_convert_buf);
            g_convert_buf = static_cast<uint32_t*>(malloc(total_pixels * 4));
            g_convert_buf_size = total_pixels;
        }
        if (!g_convert_buf) return;

        const uint8_t *src = static_cast<const uint8_t*>(data);
        uint32_t *dst = g_convert_buf;

        // copyPixelsFromBuffer for ARGB_8888 bitmaps expects RGBA byte
        // order on this device. On little-endian ARM that means we need to
        // pack the uint32_t as 0xAABBGGRR so memory bytes are [RR,GG,BB,AA].
        if (g_pixel_format == RETRO_PIXEL_FORMAT_RGB565) {
            for (unsigned y = 0; y < height; y++) {
                const uint16_t *row = reinterpret_cast<const uint16_t*>(src + y * pitch);
                uint32_t *out = dst + y * width;
                for (unsigned x = 0; x < width; x++) {
                    uint16_t px = row[x];
                    uint8_t r = g_r5_to_8[(px >> 11) & 0x1F];
                    uint8_t g = g_g6_to_8[(px >> 5)  & 0x3F];
                    uint8_t b = g_b5_to_8[px & 0x1F];
                    out[x] = 0xFF000000u
                           | (static_cast<uint32_t>(b) << 16)
                           | (static_cast<uint32_t>(g) << 8)
                           |  static_cast<uint32_t>(r);
                }
            }
        } else {
            // 0RGB1555
            for (unsigned y = 0; y < height; y++) {
                const uint16_t *row = reinterpret_cast<const uint16_t*>(src + y * pitch);
                uint32_t *out = dst + y * width;
                for (unsigned x = 0; x < width; x++) {
                    uint16_t px = row[x];
                    uint8_t r = (uint8_t)(((px >> 10) & 0x1F) * 255 / 31);
                    uint8_t g = (uint8_t)(((px >> 5) & 0x1F) * 255 / 31);
                    uint8_t b = (uint8_t)((px & 0x1F) * 255 / 31);
                    out[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
                }
            }
        }

    }

    // Send converted buffer to Java — reuse cached DirectByteBuffer
    size_t buf_size = total_pixels * 4;
    if (g_video_bytebuffer == nullptr || g_video_bytebuffer_size != buf_size) {
        if (g_video_bytebuffer) env->DeleteGlobalRef(g_video_bytebuffer);
        jobject local = env->NewDirectByteBuffer(g_convert_buf,
                                                  static_cast<jlong>(buf_size));
        if (!local) return;
        g_video_bytebuffer = env->NewGlobalRef(local);
        g_video_bytebuffer_size = buf_size;
        env->DeleteLocalRef(local);
    }

    env->CallStaticVoidMethod(g_bridge_class, g_mid_onVideoRefresh,
                              g_video_bytebuffer,
                              static_cast<jint>(width),
                              static_cast<jint>(height),
                              static_cast<jint>(width * 4),
                              static_cast<jint>(RETRO_PIXEL_FORMAT_XRGB8888));
}

// Flush accumulated audio samples to Java in a single JNI call
static void flush_audio_buffer(JNIEnv *env) {
    if (g_audio_buf_pos == 0) return;

    if (env->ExceptionCheck()) { env->ExceptionClear(); }

    jint sample_count = static_cast<jint>(g_audio_buf_pos);

    // Reuse pre-allocated array, only reallocate if too small
    if (g_audio_jarray == nullptr || g_audio_jarray_size < sample_count) {
        if (g_audio_jarray) env->DeleteGlobalRef(g_audio_jarray);
        jint new_size = sample_count > 4096 ? sample_count : 4096;
        jshortArray local = env->NewShortArray(new_size);
        if (!local) { g_audio_buf_pos = 0; return; }
        g_audio_jarray = static_cast<jshortArray>(env->NewGlobalRef(local));
        g_audio_jarray_size = new_size;
        env->DeleteLocalRef(local);
    }

    env->SetShortArrayRegion(g_audio_jarray, 0, sample_count, g_audio_buf);
    env->CallStaticVoidMethod(g_bridge_class, g_mid_onAudioSampleBatch,
                              g_audio_jarray, static_cast<jint>(g_audio_buf_pos / 2));
    g_audio_buf_pos = 0;
}

static void ensure_audio_buf(size_t needed) {
    if (g_audio_buf_capacity < needed) {
        free(g_audio_buf);
        g_audio_buf_capacity = needed > 8192 ? needed : 8192;
        g_audio_buf = static_cast<int16_t*>(malloc(g_audio_buf_capacity * sizeof(int16_t)));
    }
}

static void cb_audio_sample(int16_t left, int16_t right) {
    // Buffer single samples — will be flushed after retro_run returns
    ensure_audio_buf(g_audio_buf_pos + 2);
    if (g_audio_buf) {
        g_audio_buf[g_audio_buf_pos++] = left;
        g_audio_buf[g_audio_buf_pos++] = right;
    }
}

static size_t cb_audio_sample_batch(const int16_t *data, size_t frames) {
    // Buffer batch samples — will be flushed after retro_run returns
    size_t samples = frames * 2;
    ensure_audio_buf(g_audio_buf_pos + samples);
    if (g_audio_buf) {
        memcpy(g_audio_buf + g_audio_buf_pos, data, samples * sizeof(int16_t));
        g_audio_buf_pos += samples;
    }
    return frames;
}

static void cb_input_poll() {
    JNIEnv *env = getEnv();
    if (!env) return;
    env->CallStaticVoidMethod(g_bridge_class, g_mid_onInputPoll);
}

static int16_t cb_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    JNIEnv *env = getEnv();
    if (!env) return 0;
    jint result = env->CallStaticIntMethod(g_bridge_class, g_mid_onInputState,
                                           static_cast<jint>(port),
                                           static_cast<jint>(device),
                                           static_cast<jint>(index),
                                           static_cast<jint>(id));
    return static_cast<int16_t>(result);
}

// ── JNI native method implementations ───────────────────────────────────

extern "C" {

// --- nativeLoadCore ---
JNIEXPORT jboolean JNICALL
Java_com_bikearcade_emulator_LibretroBridge_nativeLoadCore(
    JNIEnv *env, jobject /* this */, jstring corePath)
{
    if (g_core_handle) {
        LOGW("nativeLoadCore: A core is already loaded; unload it first");
        return JNI_FALSE;
    }

    const char *path = env->GetStringUTFChars(corePath, nullptr);
    if (!path) return JNI_FALSE;

    LOGI("Loading core: %s", path);
    g_core_handle = dlopen(path, RTLD_LAZY);
    env->ReleaseStringUTFChars(corePath, path);

    if (!g_core_handle) {
        LOGE("dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }

    // Resolve all libretro symbols
    LOAD_SYM(g_core_handle, retro_set_environment);
    LOAD_SYM(g_core_handle, retro_set_video_refresh);
    LOAD_SYM(g_core_handle, retro_set_audio_sample);
    LOAD_SYM(g_core_handle, retro_set_audio_sample_batch);
    LOAD_SYM(g_core_handle, retro_set_input_poll);
    LOAD_SYM(g_core_handle, retro_set_input_state);
    LOAD_SYM(g_core_handle, retro_init);
    LOAD_SYM(g_core_handle, retro_deinit);
    LOAD_SYM(g_core_handle, retro_api_version);
    LOAD_SYM(g_core_handle, retro_get_system_info);
    LOAD_SYM(g_core_handle, retro_get_system_av_info);
    LOAD_SYM(g_core_handle, retro_load_game);
    LOAD_SYM(g_core_handle, retro_unload_game);
    LOAD_SYM(g_core_handle, retro_run);
    LOAD_SYM(g_core_handle, retro_reset);

    // Minimum required symbols
    if (!p_retro_init || !p_retro_deinit || !p_retro_run ||
        !p_retro_load_game || !p_retro_unload_game) {
        LOGE("Core is missing required symbols");
        dlclose(g_core_handle);
        g_core_handle = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

// --- nativeUnloadCore ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_nativeUnloadCore(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (g_core_handle) {
        dlclose(g_core_handle);
        g_core_handle = nullptr;
        LOGI("Core unloaded");
    }

    // Clear all function pointers
    p_retro_set_environment = nullptr;
    p_retro_set_video_refresh = nullptr;
    p_retro_set_audio_sample = nullptr;
    p_retro_set_audio_sample_batch = nullptr;
    p_retro_set_input_poll = nullptr;
    p_retro_set_input_state = nullptr;
    p_retro_init = nullptr;
    p_retro_deinit = nullptr;
    p_retro_api_version = nullptr;
    p_retro_get_system_info = nullptr;
    p_retro_get_system_av_info = nullptr;
    p_retro_load_game = nullptr;
    p_retro_unload_game = nullptr;
    p_retro_run = nullptr;
    p_retro_reset = nullptr;

    g_pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
}

// --- retroApiVersion ---
JNIEXPORT jint JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroApiVersion(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (!p_retro_api_version) return 0;
    return static_cast<jint>(p_retro_api_version());
}

// --- retroInit ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroInit(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_init) p_retro_init();
}

// --- retroDeinit ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroDeinit(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_deinit) p_retro_deinit();
}

// --- retroGetSystemInfo ---
JNIEXPORT jobjectArray JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroGetSystemInfo(
    JNIEnv *env, jobject /* this */)
{
    struct retro_system_info info = {};
    if (p_retro_get_system_info) {
        p_retro_get_system_info(&info);
    }

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(5, stringClass, env->NewStringUTF(""));

    env->SetObjectArrayElement(result, 0,
        env->NewStringUTF(info.library_name ? info.library_name : ""));
    env->SetObjectArrayElement(result, 1,
        env->NewStringUTF(info.library_version ? info.library_version : ""));
    env->SetObjectArrayElement(result, 2,
        env->NewStringUTF(info.valid_extensions ? info.valid_extensions : ""));
    env->SetObjectArrayElement(result, 3,
        env->NewStringUTF(info.need_fullpath ? "1" : "0"));
    env->SetObjectArrayElement(result, 4,
        env->NewStringUTF(info.block_extract ? "1" : "0"));

    return result;
}

// --- retroGetSystemAvInfo ---
JNIEXPORT jdoubleArray JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroGetSystemAvInfo(
    JNIEnv *env, jobject /* this */)
{
    struct retro_system_av_info av_info = {};
    if (p_retro_get_system_av_info) {
        p_retro_get_system_av_info(&av_info);
    }

    // Return [fps, sample_rate, base_width, base_height]
    jdoubleArray result = env->NewDoubleArray(4);
    jdouble vals[4] = {
        av_info.timing.fps,
        av_info.timing.sample_rate,
        static_cast<double>(av_info.geometry.base_width),
        static_cast<double>(av_info.geometry.base_height)
    };
    env->SetDoubleArrayRegion(result, 0, 4, vals);
    return result;
}

// --- retroLoadGame ---
JNIEXPORT jboolean JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroLoadGame(
    JNIEnv *env, jobject /* this */, jstring romPath)
{
    if (!p_retro_load_game) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(romPath, nullptr);
    if (!path) return JNI_FALSE;

    LOGI("Loading game: %s", path);

    // Check if core needs full path (most do) by querying system info
    struct retro_system_info sysinfo = {};
    if (p_retro_get_system_info) {
        p_retro_get_system_info(&sysinfo);
    }

    struct retro_game_info game_info = {};
    game_info.path = path;
    game_info.meta = nullptr;
    game_info.data = nullptr;
    game_info.size = 0;

    // If core does NOT need full path, we must load the file into memory
    if (!sysinfo.need_fullpath) {
        FILE *fp = fopen(path, "rb");
        if (!fp) {
            LOGE("Failed to open ROM file: %s", path);
            env->ReleaseStringUTFChars(romPath, path);
            return JNI_FALSE;
        }

        fseek(fp, 0, SEEK_END);
        long file_size = ftell(fp);
        fseek(fp, 0, SEEK_SET);

        if (file_size <= 0) {
            LOGE("Invalid ROM file size: %ld", file_size);
            fclose(fp);
            env->ReleaseStringUTFChars(romPath, path);
            return JNI_FALSE;
        }

        void *rom_data = malloc(static_cast<size_t>(file_size));
        if (!rom_data) {
            LOGE("Failed to allocate %ld bytes for ROM", file_size);
            fclose(fp);
            env->ReleaseStringUTFChars(romPath, path);
            return JNI_FALSE;
        }

        size_t bytes_read = fread(rom_data, 1, static_cast<size_t>(file_size), fp);
        fclose(fp);

        if (bytes_read != static_cast<size_t>(file_size)) {
            LOGE("Read %zu of %ld bytes", bytes_read, file_size);
            free(rom_data);
            env->ReleaseStringUTFChars(romPath, path);
            return JNI_FALSE;
        }

        game_info.data = rom_data;
        game_info.size = static_cast<size_t>(file_size);

        bool result = p_retro_load_game(&game_info);
        free(rom_data);
        env->ReleaseStringUTFChars(romPath, path);
        return result ? JNI_TRUE : JNI_FALSE;
    }

    // Core wants full path — just pass the path
    bool result = p_retro_load_game(&game_info);
    env->ReleaseStringUTFChars(romPath, path);
    return result ? JNI_TRUE : JNI_FALSE;
}

// --- retroUnloadGame ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroUnloadGame(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_unload_game) p_retro_unload_game();
}

// --- retroRun ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroRun(
    JNIEnv *env, jobject /* this */)
{
    if (p_retro_run) p_retro_run();

    // Flush any accumulated audio samples in one JNI call
    flush_audio_buffer(env);
}

// --- retroReset ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroReset(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_reset) p_retro_reset();
}

// --- retroSetEnvironment ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroSetEnvironment(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_set_environment) p_retro_set_environment(cb_environment);
}

// --- retroSetVideoRefresh ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroSetVideoRefresh(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_set_video_refresh) p_retro_set_video_refresh(cb_video_refresh);
}

// --- retroSetAudioSample ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroSetAudioSample(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_set_audio_sample) p_retro_set_audio_sample(cb_audio_sample);
}

// --- retroSetAudioSampleBatch ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroSetAudioSampleBatch(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_set_audio_sample_batch) p_retro_set_audio_sample_batch(cb_audio_sample_batch);
}

// --- retroSetInputPoll ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroSetInputPoll(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_set_input_poll) p_retro_set_input_poll(cb_input_poll);
}

// --- retroSetInputState ---
JNIEXPORT void JNICALL
Java_com_bikearcade_emulator_LibretroBridge_retroSetInputState(
    JNIEnv * /*env*/, jobject /* this */)
{
    if (p_retro_set_input_state) p_retro_set_input_state(cb_input_state);
}

} // extern "C"

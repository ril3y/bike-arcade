# BikeArcade — Technical Debt Register

**Audit Date:** 2026-03-05
**Codebase:** 7 modules, 41 Kotlin files, 1 C++ file (852 LOC), ~7,400 LOC total
**Scope:** Android (Kotlin), JNI (C++), Operational, Build/CI

---

## Prioritization Rubric

| ROI Rank | Criteria |
|----------|----------|
| **Quick Win** | S/M cost, immediate safety/reliability gain |
| **Strategic** | M/L cost, unlocks velocity or prevents class of bugs |
| **Multi-week** | L/XL cost, architectural improvement |
| **Ongoing** | Continuous discipline, process change |

---

## TIER 1 — Crash / Data Corruption / Security (Fix Now)

### JNI-001 | Critical | Cost: M

**Missing JNI ExceptionCheck after callbacks**

Evidence: `emulator/src/main/cpp/libretro_jni.cpp`
- Line 424-427: `cb_input_poll()` — `CallStaticVoidMethod` with no ExceptionCheck
- Line 431-438: `cb_input_state()` — `CallStaticIntMethod` with no ExceptionCheck
- Line 363-368: `cb_video_refresh()` — `CallStaticVoidMethod` with no ExceptionCheck
- Line 390-392: `flush_audio_buffer()` — `CallStaticVoidMethod` with no ExceptionCheck
- Line 186-210: `JNI_OnLoad` — multiple `GetStaticMethodID` without ExceptionCheck

Impact: If Java throws during a callback, the pending exception propagates into untrusted libretro core code. This causes undefined behavior — silent corruption, wrong return values, or delayed crashes that are nearly impossible to debug.

Why: JNI doesn't auto-clear exceptions; every call that can throw must be checked.

Remediation:
1. Add `env->ExceptionCheck()` + `env->ExceptionClear()` after every `Call*Method`, `NewDirectByteBuffer`, `NewGlobalRef`, `GetStaticMethodID`
2. Return safe defaults (0, nullptr) when exception detected
3. Log cleared exceptions for diagnostics

Verification: ASan + stress test with intentional Java-side throws; no crashes.

ROI: **Quick Win** — mechanical fix, prevents entire class of JNI crashes.

---

### JNI-002 | Critical | Cost: M

**Unbounded native buffer allocations from untrusted core**

Evidence: `emulator/src/main/cpp/libretro_jni.cpp`
- Line 396-402: `ensure_audio_buf()` — `malloc(needed * sizeof(int16_t))` with no upper bound
- Line 281-285: `cb_video_refresh()` — `malloc(total_pixels * 4)` with no size cap
- Line 413-422: `cb_audio_sample_batch()` — `frames * 2` can overflow on 32-bit

Impact: A malicious or buggy libretro core can request multi-GB allocations, causing OOM crashes or integer overflow leading to heap corruption.

Why: Libretro cores are loaded via `dlopen` — they are untrusted native code.

Remediation:
1. Cap audio buffer: `if (needed > MAX_AUDIO_SAMPLES) return;` (e.g. 1M samples = 2MB)
2. Cap video buffer: `if (total_pixels > 4096*4096) return;` (64MB max)
3. Add overflow check: `if (frames > SIZE_MAX / 2) return 0;`

Verification: Fuzz test with extreme width/height/frames values; no crash or allocation > caps.

ROI: **Quick Win** — 3 bounds checks prevent entire class of OOM/overflow.

---

### JNI-003 | High | Cost: M

**Thread-unsafe global state in JNI layer — no mutexes**

Evidence: `emulator/src/main/cpp/libretro_jni.cpp`
- Lines 103-160: 15+ global variables (`g_audio_buf`, `g_convert_buf`, `g_video_bytebuffer`, `g_pixel_format`, `g_core_handle`) modified without locks
- Line 238: `g_pixel_format` written in `cb_environment`, read in `cb_video_refresh`
- Line 396-402: `g_audio_buf` freed/reallocated in `ensure_audio_buf`, concurrently read in `cb_audio_sample_batch`

Impact: Race conditions between libretro callbacks on different threads — use-after-free, torn reads, heap corruption.

Why: Libretro cores may invoke audio/video/input callbacks from different threads.

Remediation:
1. Add `pthread_mutex_t` for audio state (`g_audio_buf*`), video state (`g_convert_buf*`, `g_video_bytebuffer*`), and pixel format
2. Lock around all read/write of shared globals
3. Alternative: document single-thread assumption and enforce with `assert(pthread_self() == g_emu_thread)`

Verification: ThreadSanitizer (TSan) build with no data race warnings.

ROI: **Quick Win** — straightforward mutex additions.

---

### JNI-004 | High | Cost: S

**Memory leaks on core unload**

Evidence: `emulator/src/main/cpp/libretro_jni.cpp`, lines 502-535 (`nativeUnloadCore`)
- `g_audio_buf` — allocated via malloc, never freed on unload
- `g_convert_buf` — allocated via malloc, never freed on unload
- `g_video_bytebuffer` — global JNI ref, never DeleteGlobalRef'd
- `g_audio_jarray` — global JNI ref, never DeleteGlobalRef'd

Impact: Memory leaks compound across game switches. On a 2GB device, a few unload/reload cycles could exhaust heap.

Remediation:
```c
// In nativeUnloadCore:
free(g_audio_buf); g_audio_buf = nullptr; g_audio_buf_capacity = 0; g_audio_buf_pos = 0;
free(g_convert_buf); g_convert_buf = nullptr; g_convert_buf_size = 0;
if (g_video_bytebuffer) { env->DeleteGlobalRef(g_video_bytebuffer); g_video_bytebuffer = nullptr; }
if (g_audio_jarray) { env->DeleteGlobalRef(g_audio_jarray); g_audio_jarray = nullptr; }
```

Verification: Load/unload 20 games in a row; RSS stays flat.

ROI: **Quick Win** — 8 lines of cleanup code.

---

### ANDR-001 | Critical | Cost: S

**`Thread.sleep(2000)` blocks coroutine (ANR risk)**

Evidence: `app/src/main/java/com/bikearcade/app/MainViewModel.kt`, line 284
```kotlin
private fun ensureSerialBridgeRunning() {
    // ... startActivity calls ...
    Thread.sleep(2000)  // BLOCKS for 2 seconds
}
```
Called from `viewModelScope.launch` at line 125, which runs on `Dispatchers.Main`.

Impact: Blocks the main thread for 2 seconds on every app startup. Causes visible UI freeze. On slow devices, may trigger ANR (5s threshold).

Remediation: Replace with `delay(2000)` and make function `suspend`, or move entire function to `withContext(Dispatchers.IO)`.

Verification: StrictMode disk/network policy on main thread; no violations.

ROI: **Quick Win** — 1-line change.

---

## TIER 2 — Lifecycle / Concurrency Leaks (Fix Soon)

### ANDR-002 | High | Cost: M

**Unmanaged CoroutineScopes — coroutine leaks**

Evidence: 4 classes create `CoroutineScope(Job())` or `CoroutineScope(SupervisorJob())` not tied to any lifecycle:

| File | Line | Scope |
|------|------|-------|
| `media/…/BikeArcadeMediaController.kt` | 34 | `CoroutineScope(Dispatchers.Main + Job())` |
| `media/…/MediaSessionMonitor.kt` | 47 | `CoroutineScope(Dispatchers.Main + Job())` |
| `metrics/…/tcp/TcpSerialClient.kt` | 77 | `CoroutineScope(SupervisorJob() + Dispatchers.IO)` |
| `metrics/…/BikeMetricsService.kt` | 52 | `CoroutineScope(SupervisorJob() + Dispatchers.Default)` |

Impact: If `release()`/`destroy()`/`disconnect()` is not called (process kill, exception during teardown), coroutines run forever — polling loops drain battery, reconnection loops spam logs, position-polling jobs accumulate.

Why: Manual lifecycle management instead of structured concurrency.

Remediation:
1. Accept a parent `CoroutineScope` (or `Job`) in constructors
2. Wire to `viewModelScope` from MainViewModel
3. Child scopes automatically cancel when parent (ViewModel) is cleared
4. Alternative: convert to lifecycle-aware Android Services

Verification: Force-stop app mid-use; logcat shows no continued coroutine activity from these classes.

ROI: **Strategic** — prevents battery drain and resource leaks across the board.

---

### ANDR-003 | Medium | Cost: S

**Swallowed exceptions without logging**

Evidence:
| File | Line | Context |
|------|------|---------|
| `app/…/MainViewModel.kt` | 282 | `catch (_: Exception) { }` in serialbridge startup — no log |
| `emulator/…/EmulatorSurface.kt` | 148 | `catch (_: Exception) { }` in `submitFrame()` — video errors silently dropped |

Impact: Silent failures make debugging impossible. Serialbridge may silently not start; video rendering may silently stop.

Remediation: Add `Log.w(TAG, "...", e)` to both catch blocks.

Verification: Grep for `catch.*Exception.*\{\s*\}` — zero results without logging.

ROI: **Quick Win** — 2 lines of logging.

---

## TIER 3 — Architecture Violations (Fix to Unblock Velocity)

### ANDR-004 | Medium | Cost: L

**No dependency injection — manual AppContainer blocks testing**

Evidence: `app/…/BikeArcadeApplication.kt`, lines 32-42
```kotlin
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val bikeMetricsService = BikeMetricsService(context)
    val mediaSessionMonitor = MediaSessionMonitor(context)
    // ... all concrete, no interfaces
}
```

Impact: Cannot swap implementations for testing. No fake metrics source, no fake media controller, no test clock. Every class hard-wires to production dependencies.

Why: Started simple, never introduced DI framework.

Remediation:
1. Define interfaces for core services: `MetricsSource`, `MediaMonitor`, `Clock`, `DispatcherProvider`
2. Adopt Hilt or Koin for scoped DI
3. Create test modules with fakes
4. Wire AppContainer through DI graph

Verification: Unit test for MainViewModel runs with all-fake dependencies, no Android framework needed.

ROI: **Strategic** — unlocks all testing; gate for ANDR-007.

---

### ANDR-005 | Medium | Cost: M

**Duplicate MediaInfo data classes — type bridge overhead**

Evidence:
- `media/…/MediaInfo.kt`: `data class MediaInfo(trackName, artistName, albumArt: Bitmap?, ...)`
- `overlay/…/MediaInfo.kt`: `data class MediaInfo(trackName, artist, albumArtUri: String?, ...)`
- `app/…/MainViewModel.kt`, lines 88-95: manual field-by-field mapping with `albumArtUri = null` (data loss)

Impact: Bitmap data lost in conversion. Field name inconsistencies (`artistName` vs `artist`, `duration` vs `durationMs`). Changes to either class silently break the bridge.

Remediation:
1. Unify to single `MediaInfo` in `:media` module (without Bitmap — use URI or content path)
2. Have `:overlay` depend on `:media` (already does via `:app`)
3. Delete overlay's MediaInfo and import alias in MainViewModel

Verification: Single `MediaInfo` class in codebase; no import alias needed.

ROI: **Quick Win** — delete one file, update imports.

---

### ANDR-006 | Medium | Cost: M

**MainViewModel is a god-init — 112-line init block**

Evidence: `app/…/MainViewModel.kt`, lines 63-174
- 4 separate `viewModelScope.launch` blocks for metrics, media, sessions, TCP connection
- Inline Intent construction and activity launching
- `Thread.sleep`, `releaseSerialBridge()`, `ensureSerialBridgeRunning()` all in init

Impact: Hard to test, hard to read, single point of failure. Any exception in init kills the entire ViewModel.

Remediation:
1. Extract to named private methods: `observeMetrics()`, `observeMedia()`, `connectToSerialBridge()`
2. Move serial bridge logic to a `SerialBridgeManager` class
3. Move media session setup to a `MediaSessionSetup` class

Verification: Init block < 20 lines; each concern independently testable.

ROI: **Strategic** — improves readability and testability.

---

### ANDR-007 | Medium | Cost: M

**No testability seams — hardcoded system calls everywhere**

Evidence (consolidated from abstraction audit):

| System Call | Locations | Count |
|-------------|-----------|-------|
| `System.currentTimeMillis()` | GameLibrary:88, GameLauncherScreen:549, BikeMetrics:23, MetricsProcessor:79,105 | 5 |
| `System.nanoTime()` | EmulatorThread:112,126,141,152,156 | 5 |
| `Dispatchers.IO/Main/Default` | 7 files | 10+ |
| `android.util.Log.*` | All modules | 113+ |
| `Settings.Secure.getString` | MediaSessionMonitor:62 | 1 |

Impact: Zero unit tests possible for any business logic that touches time, threading, or logging.

Remediation:
1. Create `interface Clock { fun currentTimeMillis(): Long; fun nanoTime(): Long }`
2. Create `interface DispatcherProvider { val main; val io; val default }`
3. Inject into all services via DI (ANDR-004)

Verification: Unit tests for MetricsProcessor, GameLibrary run without Android framework.

ROI: **Strategic** — prerequisite for any meaningful test coverage.

---

## TIER 4 — Boundary Typing / Validation (Fix to Prevent Silent Failures)

### ANDR-008 | High | Cost: M

**TCP protocol parser: no CRC validation, hardcoded byte offsets**

Evidence: `metrics/…/tcp/TcpSerialClient.kt`
- Line 252: "Skip CRC validation for now" — comment in code
- Lines 334-348: Byte offsets hardcoded as magic numbers (3, 7, 4-5, 8-9, 18-19)
- Line 327: Only validates `data.size < 20` — no maximum
- Lines 444-459: Endianness assumed, not validated

Impact: Corrupted frames produce wrong metrics silently. Without CRC, any byte flip in the serial bridge passes through. Hardcoded offsets are fragile and undocumented.

Remediation:
1. Enable CRC32 validation (already have `calculateCrc32` at line 414)
2. Extract byte offsets to named constants: `OFFSET_RESISTANCE = 3`, `OFFSET_CADENCE = 7`, etc.
3. Add `require(data.size == 23)` for exact payload size
4. Add unit tests with captured real frames

Verification: Parser unit tests with valid/invalid/corrupted frames; 100% branch coverage.

ROI: **Strategic** — prevents silent bad data reaching the UI and workout stats.

---

### ANDR-009 | Medium | Cost: M

**BLE FTMS parser: 127-line function, no tests**

Evidence: `metrics/…/ble/FtmsManager.kt`, lines 269-395 (`parseIndoorBikeData`)
- Single 127-line function with nested flag checks
- Proper bounds checking (better than TCP parser)
- But zero test coverage

Remediation:
1. Extract per-field parsers: `parseSpeed()`, `parseCadence()`, `parsePower()`
2. Add unit tests with known BLE characteristic values from FTMS spec
3. Test edge cases: empty payload, all-flags-set, oversized payload

Verification: Unit tests for each FTMS field; fuzz test with random payloads.

ROI: **Strategic** — BLE is the primary metrics source for standard bikes.

---

## TIER 5 — Build / CI / Tooling (Fix to Prevent Drift)

### BUILD-001 | Medium | Cost: S

**CMakeLists.txt missing security compiler flags**

Evidence: `emulator/src/main/cpp/CMakeLists.txt` (24 lines total)
- No `-fstack-protector-strong`
- No `-D_FORTIFY_SOURCE=2`
- No `-Werror -Wall -Wextra`
- No ASan/UBSan in debug builds

Remediation:
```cmake
target_compile_options(retro_jni PRIVATE
    -Wall -Wextra -Werror
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
)
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(retro_jni PRIVATE -fsanitize=address,undefined)
    target_link_options(retro_jni PRIVATE -fsanitize=address,undefined)
endif()
```

Verification: Debug build runs under ASan with no violations.

ROI: **Quick Win** — 10 lines of CMake.

---

### BUILD-002 | Medium | Cost: M

**Zero test infrastructure**

Evidence: No files found in any `src/test/` or `src/androidTest/` directory across all 7 modules.

Impact: Every change is manually verified. Regressions are discovered by users. Protocol parsers, metrics processors, and state machines have zero automated coverage.

Critical untested paths:
- TCP serial protocol parsing
- BLE FTMS data parsing
- MetricsProcessor session stats / calorie calculation
- EmulatorCore state machine (IDLE → LOADED → RUNNING)
- Save state serialize/deserialize roundtrip
- GameLibrary ROM scanning and filtering

Remediation:
1. Add `testImplementation` dependencies (JUnit5, MockK, Turbine for Flows)
2. Start with protocol parsers (highest risk, pure functions)
3. Add MetricsProcessor tests (calorie/session calculations)
4. Add EmulatorCore state machine tests
5. Add Compose UI tests for critical flows

Verification: `./gradlew testDebugUnitTest` passes with > 0 tests per module.

ROI: **Strategic** — foundational; all future changes get safer.

---

### BUILD-003 | Medium | Cost: M

**No CI/CD pipeline**

Evidence: No `.github/workflows/`, no Jenkinsfile, no CI config of any kind.

Impact: No automated build verification, no lint gates, no dependency scanning, no APK size tracking.

Remediation: GitHub Actions workflow:
```yaml
- ./gradlew lint detekt
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug
- dependency vulnerability scan (OSV-Scanner or Gradle versions plugin)
```

Verification: PR cannot merge without green CI.

ROI: **Strategic** — prevents regression, enforces quality gates.

---

### BUILD-004 | Low | Cost: S

**No static analysis (Detekt/Lint config)**

Evidence: No `detekt.yml`, no `lint.xml`, no Spotless/ktfmt configuration.

Remediation:
1. Add Detekt plugin with default rules + complexity thresholds
2. Add Spotless for consistent formatting
3. Configure Android Lint baselines

Verification: `./gradlew detekt` and `./gradlew lint` pass.

ROI: **Quick Win** — catches issues automatically.

---

### BUILD-005 | Low | Cost: S

**ProGuard rules missing JNI keeps**

Evidence: `app/proguard-rules.pro` — only comments, no rules. `emulator/proguard-rules.pro` — empty.

Impact: Release builds may obfuscate JNI method names, causing `UnsatisfiedLinkError` at runtime.

Remediation:
```proguard
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.bikearcade.emulator.LibretroBridge { *; }
```

Verification: Release APK runs without JNI link errors.

ROI: **Quick Win** — 2 lines prevent release-only crashes.

---

## TIER 6 — Performance / Polish

### ANDR-010 | Low | Cost: S

**BikeMetrics.timestamp defaults to System.currentTimeMillis()**

Evidence: `metrics/…/BikeMetrics.kt`, line 23
```kotlin
val timestamp: Long = System.currentTimeMillis()
```

Impact: Every `BikeMetrics()` construction captures wall-clock time as a side effect. This makes the data class non-deterministic and harder to test.

Remediation: Remove default; require callers to pass timestamp explicitly.

ROI: **Quick Win** — cleaner API.

---

### ANDR-011 | Low | Cost: S

**Long parameter lists in Composables**

Evidence:
- `SidebarOverlay`: 13 parameters (overlay/SidebarOverlay.kt:46-60)
- `PauseOverlay`: 11 parameters (app/PauseOverlay.kt:61-73)
- `GameLauncherHeader`: 8 parameters (app/launcher/GameLauncherScreen.kt:161-170)

Impact: Easy to pass wrong callback to wrong parameter. Hard to read call sites.

Remediation: Group into state holder classes:
```kotlin
data class SidebarState(metrics: BikeMetrics, mediaInfo: MediaInfo, ...)
data class SidebarCallbacks(onPlayPause: () -> Unit, onSkipNext: () -> Unit, ...)
```

ROI: **Multi-week** — touch many call sites.

---

### JNI-005 | Low | Cost: S

**Hardcoded native paths**

Evidence: `emulator/src/main/cpp/libretro_jni.cpp`, lines 171-172
```c
static char g_system_dir[512] = "/storage/emulated/0/BikeArcade/system";
static char g_save_dir[512]   = "/storage/emulated/0/BikeArcade/saves";
```

Impact: Paths don't adapt to different storage locations or multi-user setups.

Remediation: Pass paths from Kotlin via JNI during core initialization.

ROI: **Quick Win** — pass as parameters to init function.

---

## Summary by Severity

| Severity | Count | IDs |
|----------|-------|-----|
| **Critical** | 3 | JNI-001, JNI-002, ANDR-001 |
| **High** | 4 | JNI-003, JNI-004, ANDR-002, ANDR-008 |
| **Medium** | 9 | ANDR-003 through ANDR-009, BUILD-001 through BUILD-003 |
| **Low** | 5 | ANDR-010, ANDR-011, BUILD-004, BUILD-005, JNI-005 |

## Recommended Fix Order (ROI-prioritized)

### Phase 1 — Quick Wins (1-2 days)
1. **ANDR-001** — Replace `Thread.sleep` with `delay` (S)
2. **ANDR-003** — Add logging to swallowed exceptions (S)
3. **JNI-004** — Free buffers in `nativeUnloadCore` (S)
4. **JNI-002** — Add size caps to audio/video buffers (S)
5. **BUILD-005** — Add ProGuard JNI keep rules (S)
6. **BUILD-001** — Add CMake security flags (S)
7. **ANDR-005** — Unify MediaInfo classes (M)

### Phase 2 — Safety (3-5 days)
8. **JNI-001** — Add ExceptionCheck to all JNI callbacks (M)
9. **JNI-003** — Add pthread mutexes for shared state (M)
10. **ANDR-002** — Wire coroutine scopes to lifecycle (M)

### Phase 3 — Foundation (1-2 weeks)
11. **BUILD-002** — Add test infrastructure + protocol parser tests (M)
12. **ANDR-008** — Enable CRC validation, named constants for offsets (M)
13. **ANDR-006** — Refactor MainViewModel init block (M)
14. **BUILD-004** — Add Detekt + Lint (S)

### Phase 4 — Strategic (2-4 weeks)
15. **ANDR-004** — Introduce DI framework (L)
16. **ANDR-007** — Add Clock/DispatcherProvider seams (M)
17. **ANDR-009** — Refactor + test BLE parser (M)
18. **BUILD-003** — Set up CI/CD pipeline (M)
19. **ANDR-011** — Refactor long parameter lists (M)

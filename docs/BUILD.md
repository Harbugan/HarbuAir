# Build instructions

**Status: UNVERIFIED.** The Android project was written in an environment where `dl.google.com` is blocked (HTTP 403 from the proxy), so the Android Gradle Plugin, SDK and NDK could not be fetched. Evidence: `gradle build` fails with `Plugin [id: 'com.android.application', version: '8.7.3'] was not found` (2026-11-12). **No APK has been built and no Kotlin or C++ code in `app/` has been compiled.** Expect to fix compile errors on the first real build.

## Prerequisites

- JDK 17, Android Studio Ladybug (2024.2) or newer, or command-line SDK
- Android SDK Platform 35, Build-Tools 35.x, **NDK 27.2.12479018**, CMake 3.22.1 (SDK Manager)
- Network access to github.com on first configure (the sender library's CMake downloads Mbed TLS 3.6.0 by pinned commit; no other downloads)
- Git submodule checked out

## Steps

```bash
git clone --recurse-submodules <repo-url> HarbuAir
cd HarbuAir
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # adjust path; file is git-ignored
./gradlew :app:testDebugUnitTest      # JVM unit tests (tone generator etc.)
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you cloned without `--recurse-submodules`: `git submodule update --init`.

## Notes

- ABI: `arm64-v8a` only (Xperia XZ1 Compact is ARM64). Edit `abiFilters` for an emulator.
- minSdk 34 (uses `NsdManager.registerServiceInfoCallback`), compile/target 35.
- `local.properties`, `build/`, `.cxx/` are git-ignored. No signing keys are in the repo; debug APK uses the default debug keystore.
- Host-side sanity build of the sender library alone (not the app): `cmake -S third_party/airplay2-sender-cpp -B /tmp/b && cmake --build /tmp/b && ctest --test-dir /tmp/b`. I did **not** get to run this (command was not permitted in the authoring session), so upstream's test status is also unverified by us.
- Root is **not** needed anywhere in Phase 1.

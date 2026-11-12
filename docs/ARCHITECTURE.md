# HarbuAir technical architecture and evidence log

**Status:** Phase 1 code written, **not yet compiled or run on hardware** (see docs/BUILD.md, docs/TESTING.md). Evidence for the sender choice: docs/AIRPLAY_RESEARCH.md.

## Goal and constraints

Send live stereo line-level audio from an Android-attached USB audio interface to an existing HomePod stereo pair on the same LAN. No Mac, Raspberry Pi, external bridge, or cloud service in the preferred design.

## Proposed pipeline

```text
Turntable
  -> RIAA phono preamp (built-in or external)
  -> stereo analog line out
  -> USB audio interface with stereo line input
  -> Android USB host / audio input
  -> PCM stereo capture
  -> optional format conversion, buffering, drift handling
  -> AirPlay sender (UNSELECTED / UNVERIFIED)
  -> local Wi-Fi
  -> HomePod stereo pair (UNVERIFIED)
```

## Architecture components

| Component | Candidate | Status |
| --- | --- | --- |
| Android shell | Native Kotlin app | Proposed |
| USB input | Android AudioRecord with supported external routing; investigate USB APIs | Unverified |
| Device discovery | mDNS / DNS-SD | Proposed |
| Sender protocol | Research required; do not assume AirPlay 1 equals AirPlay 2 | Blocked on evidence |
| Pairing / authentication | Implementation-dependent | Unknown |
| Stream format | PCM/ALAC as supported by proven sender | Unknown |
| Service lifecycle | Foreground service with visible notification and stop | Proposed |
| Output target | HomePod stereo pair, also used with Apple TV | Hardware test required |

## Primary technical risks

1. **AirPlay sender availability:** many popular open-source AirPlay projects implement receivers, not senders.
2. **AirPlay 2 and HomePod pairing:** discovery alone does not imply authorization or working playback.
3. **Stereo-pair semantics:** one logical target may be advertised; verify stereo channels and interaction with Apple TV.
4. **Clocking and latency:** live sources have no rewind; sustained timing drift causes under/overruns.
5. **Android USB capture:** some devices/adapters expose playback only or mono mic inputs.
6. **Licensing:** GPL/LGPL and other licenses may constrain distribution and linking.
7. **Background restrictions:** microphone/audio capture and foreground service policies vary by Android version.

## Evidence standard

For each candidate implementation record:
- Project URL, commit/tag and review date.
- License and distribution implications.
- Sender or receiver? Supported protocol and codec?
- Android integration feasibility, build requirements and native dependencies.
- HomePod / stereo-pair evidence (link to code or reproducible test).
- Authentication, encryption, pairing and timing requirements.
- What was actually tested, with device model and result.

Do not treat a project's README claims as hardware verification.

## Experiment 1: minimum viable sender

**Hypothesis:** an Android device can establish an authorized audio session and stream an original generated stereo test tone to the paired HomePods.

**Procedure (to refine after research):**
1. Record exact test device, OS, network and HomePod pair.
2. Discover advertised services without exposing device identifiers in git.
3. Select the logical stereo-pair target and establish a session.
4. Send generated audio with distinct left/right signals at safe levels.
5. Verify both channels, startup delay, 10-minute stability, stop/restart, and behavior when Apple TV is active.
6. Capture sanitized logs and reproducible results.

**Pass:** repeatable audio on both HomePods with correct stereo and controllable stop. **Fail:** no supported authenticated session or unfixable transport behavior. **Inconclusive:** discovery only, emulator-only, mock receiver, or untested library.

## Decision record

- ADR-001 (proposed): native Kotlin rather than web/PWA because reliable USB capture and foreground audio routing need Android platform APIs.
- ADR-002 (accepted provisionally, 2026-11-12): use `airplay2-sender-cpp` (Apache-2.0) as native sender, pinned as submodule at `fc91350`. Rationale and unverified assumptions in AIRPLAY_RESEARCH.md. Revisit if Gate A fails.
- ADR-004: minSdk 34 / arm64-v8a only for Phase 1 (target device is Android 15); NsdManager (no permissions) for discovery instead of a bundled mDNS stack.
- ADR-005: no foreground service in Phase 1. Streaming stops in `onStop`, so nothing can run unseen. A foreground service is required in Phase 3 for screen-off use.
- ADR-003 (pending): if pure Android is infeasible, compare local bridge options and request explicit owner approval before changing architecture.

## Phase 1 module map (as implemented)

```text
ui/MainActivity, ui/StreamViewModel   UI + session state machine (ConnState)
discovery/AirPlayDiscovery, Receiver  NsdManager: _airplay._tcp + _raop._tcp, TXT parsing, merge by name
audio/AudioSource, ToneSource         PCM producer interface (int16 stereo); tone = 440 Hz, -20 dBFS, both/left/right
engine/NativeEngine, CredentialStore  JNI wrapper; app-private pairing store; random per-install sender MAC
diag/DiagLog                          in-memory log (300 lines) with secret-like token masking
cpp/native_engine.cpp                 loop thread + command queue + SPSC PCM ring around RaopSender/RaopLoop
third_party/airplay2-sender-cpp       submodule (Apache-2.0), pulls Mbed TLS at CMake configure time
```

Pipeline: `AudioSource.read()` (real-time paced coroutine, 0.5 s lead) -> JNI `pushPcm` -> lock-free ring (~3 s @ 44.1 kHz) -> library (linear resample to 44.1 kHz if needed, ALAC, ChaCha20-Poly1305, RTP/UDP) -> receiver. Stereo is preserved end-to-end (interleaved L/R, no downmix code). Sample format int16; the tone uses 44.1 kHz so no resampling occurs. Clock sync: the library answers the receiver's NTP-style timing requests; drift handling for a live source is **not** implemented (Phase 3). Threading: all `RaopSender` calls on one native thread; other threads only enqueue commands or push the ring.

To add USB capture later: implement `AudioSource` (AudioRecord), no engine change. Root is not used.

## Test log

| Date | Hardware | Test | Result | Evidence |
| --- | --- | --- | --- | --- |
| 2026-11-12 | Not yet specified | Project planning | Not tested | Initial architecture |
| 2026-11-12 | none (cloud container) | Source review of both candidate senders | Done (reading only) | docs/AIRPLAY_RESEARCH.md |
| 2026-11-12 | none (cloud container) | `gradle build` of Android project | **FAILED to start**: AGP unresolvable, dl.google.com blocked | docs/BUILD.md |
| 2026-11-12 | Xperia XZ1C + Apple TV/HomePods | Real AirPlay session | **UNVERIFIED** (not run) | docs/TESTING.md |

## Questions for owner

- Android phone model and version?
- Turntable model and whether it has a built-in phono preamp?
- USB audio interface model (if already owned)?
- HomePod generation and Apple TV model?
- Can HomePods be selected as an AirPlay target while Apple TV is in use?

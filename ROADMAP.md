# HarbuAir roadmap

Status legend: TODO · IN PROGRESS · BLOCKED · VERIFIED · NOT FEASIBLE.

## Phase 0 — Requirements and hardware inventory (TODO)

- [ ] Confirm Android model, Android version and USB host capability.
- [ ] Confirm turntable model, built-in phono stage, analog output and cable path.
- [ ] Confirm USB audio interface and stereo line-input compatibility.
- [ ] Confirm HomePod models, stereo-pair configuration, Apple TV relationship and Wi-Fi topology.
- [ ] Define acceptable startup delay, dropouts and audio latency.

**Exit:** complete, realistic signal chain and test setup.

## Phase 1 — AirPlay feasibility (IN PROGRESS; code written, hardware BLOCKED/UNVERIFIED; highest risk)

- [x] Audit existing sender implementations (not receivers) and licenses. (docs/AIRPLAY_RESEARCH.md; choice: airplay2-sender-cpp)
- [x] Identify protocol support (from source/README only; unverified on hardware).
- [ ] Test mDNS discovery of the actual paired destination. (implemented, UNVERIFIED)
- [ ] Establish authorized session from Android. (implemented, UNVERIFIED; project never compiled)
- [ ] Play generated left/right stereo test audio on both HomePods. (tone source with L/R/both implemented, UNVERIFIED)
- [ ] Test stop, restart, reconnection, and Apple TV interaction.
- [ ] Document results, logs, constraints and go/no-go.

**Gate A:** real-device HomePod stereo-pair playback verified. If not possible, document blockers and ask before pursuing a bridge/server fallback.

## Phase 2 — Live USB audio capture (TODO)

- [ ] Detect USB stereo interface and obtain user-approved audio input.
- [ ] Capture PCM stereo; report negotiated format.
- [ ] Input meter, clipping and channel test.
- [ ] Handle unplug/replug and denied permissions.
- [ ] Validate noise floor and phono preamp signal level.

**Gate B:** stable stereo capture from real turntable hardware.

## Phase 3 — End-to-end streaming (TODO)

- [ ] Connect capture to sender pipeline.
- [ ] Buffer/resample safely and handle clock drift.
- [ ] Measure latency, dropouts, channel integrity and long-session stability.
- [ ] Add visible foreground service and explicit stop control.
- [ ] Recover gracefully from network or device interruptions.

**Gate C:** repeatable vinyl-to-HomePod playback on target hardware.

## Phase 4 — Usable MVP (TODO)

- [ ] Simple device/input selection and Start/Stop.
- [ ] Connection state, input levels and clear errors.
- [ ] Persistent settings only where helpful.
- [ ] Accessibility and Android permission rationale.
- [ ] Unit tests and smoke-test checklist.

## Phase 5 — Distribution (TODO)

- [ ] Build reproducible debug APK.
- [ ] GitHub Actions CI and downloadable artifacts.
- [ ] Document sideload installation and Android version requirements.
- [ ] Review dependency licenses, security and privacy.
- [ ] Consider signed release APK after hardware validation.

## Phase 1 follow-ups / Phase 2 prerequisites

1. First real build with Android SDK/NDK (BUILD.md); fix compile errors; run unit tests.
2. Run TESTING.md T1-T7 on the Xperia + Apple TV/HomePods; fill the evidence log; decide Gate A and the option A/B/C routing.
3. If Gate A passes: foreground service + audio focus, Wi-Fi change handling, pairing-credential encryption (Keystore), default strict receiver auth, reconnect logic.
4. Then Phase 2 (USB capture behind the existing `AudioSource` interface) - do not start before Gate A.
5. Fallbacks if HomePod pair is not addressable: Apple TV endpoint only; or ask owner before any bridge architecture.

## Current next action

Owner: build the APK and run docs/TESTING.md on the real Xperia + Apple TV/HomePods; report results. Agent: fix whatever the first real build reveals.

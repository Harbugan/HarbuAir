# HarbuAir roadmap

Status legend: TODO · IN PROGRESS · BLOCKED · VERIFIED · NOT FEASIBLE.

## Phase 0 — Requirements and hardware inventory (TODO)

- [ ] Confirm Android model, Android version and USB host capability.
- [ ] Confirm turntable model, built-in phono stage, analog output and cable path.
- [ ] Confirm USB audio interface and stereo line-input compatibility.
- [ ] Confirm HomePod models, stereo-pair configuration, Apple TV relationship and Wi-Fi topology.
- [ ] Define acceptable startup delay, dropouts and audio latency.

**Exit:** complete, realistic signal chain and test setup.

## Phase 1 — AirPlay feasibility (TODO; highest risk)

- [ ] Audit existing sender implementations (not receivers) and licenses.
- [ ] Identify protocol support: AirPlay 1 vs AirPlay 2, ALAC/PCM, pairing/auth, timing.
- [ ] Test mDNS discovery of the actual paired destination.
- [ ] Establish authorized session from Android.
- [ ] Play generated left/right stereo test audio on both HomePods.
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

## Current next action

Codex: investigate Phase 1 **before implementing a full application**. Record factual evidence in docs/ARCHITECTURE.md and propose a minimal experiment. Owner: provide hardware inventory and run the on-device test.

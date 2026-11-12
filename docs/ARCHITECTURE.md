# HarbuAir technical architecture and evidence log

**Status:** working hypothesis, not an implemented or verified architecture.

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
- ADR-002 (pending): choose AirPlay sender only after evidence and real-device verification.
- ADR-003 (pending): if pure Android is infeasible, compare local bridge options and request explicit owner approval before changing architecture.

## Test log

| Date | Hardware | Test | Result | Evidence |
| --- | --- | --- | --- | --- |
| 2026-11-12 | Not yet specified | Project planning | Not tested | Initial architecture |

## Questions for owner

- Android phone model and version?
- Turntable model and whether it has a built-in phono preamp?
- USB audio interface model (if already owned)?
- HomePod generation and Apple TV model?
- Can HomePods be selected as an AirPlay target while Apple TV is in use?

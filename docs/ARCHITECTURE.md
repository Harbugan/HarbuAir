# HarbuAir technical architecture and evidence log

**Status:** Phase 1 desk research done (2026-11-12); nothing tested on hardware. No sender approved.

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

## Phase 1 findings: sender candidates (desk research, 2026-11-12)

**Method and limits.** Findings come from fetching public GitHub/docs pages through a summarising fetch tool and web searches. Source code was **not** cloned or read, the GitHub API and pyatv.dev were unreachable from this session, and nothing was run. Every row is therefore "reported", not "verified". Commit/tag pins are still TODO.

| Candidate | Role | License | Protocol (as reported) | Android fit | Evidence / unknowns |
| --- | --- | --- | --- | --- | --- |
| [pyatv](https://github.com/postlund/pyatv) | Sender/controller (Python asyncio) | MIT (badge on repo page) | RAOP (AirPlay 1-style) `stream_file`; WAV/MP3/FLAC/OGG; HAP auth only for AirPlay 2 remote-control; only *legacy* pairing for RAOP; password devices via RAOP only. Supported-features doc lists HomePod mini verified on v14.5 | No native Android; Python only (Termux/Chaquopy untested) | Streams **files/URLs**, one stream at a time; live PCM input not documented (unknown). No stereo-pair statement. Release/tag date unverified |
| [libraop (philippe44)](https://github.com/philippe44/libraop) | Sender (RAOP player + library, C) | **Unknown** (LICENSE path 404; check repo) | "RAOP v2 protocol"; interactive pairing option; AirPlay 2 **not stated** | Page lists Win/macOS/Linux; Android not mentioned, but C code is NDK-plausible (unverified) | Used by the same author in bridges (AirConnect, lms-raop). HomePod not mentioned |
| [OwnTone](https://github.com/owntone/owntone-server) | Media server with AirPlay 1+2 output | GPL-2.0 (COPYING) | AirPlay 1 and 2 receivers as outputs | Server daemon, not a library; GPL blocks linking into a permissive app | Useful as protocol reference only. HomePod/stereo pair not stated |
| [AirConnect](https://github.com/philippe44/AirConnect) | Bridge (UPnP/Cast -> AirPlay) | Unknown | Not the right direction for this project | No Android | Excluded |
| Shairport Sync, UxPlay, android-airplay-server, Kotlin AirPlay receiver, java-airplay-lib | **Receivers** | various | n/a | n/a | Excluded per AGENTS.md rule 4 |
| Music Assistant AirPlay provider | Sender (Python, uses RAOP + AirPlay 2) | Not checked | Docs: RAOP default, AirPlay 2 supported; HomePod needs "Everyone on the same network" or the right password; password pairing/PTP not yet supported | n/a | Source of the stereo-pair caveat below |
| Elixir `airplay` (hex.pm) | Sender | Not checked | Claims verified streaming to HomePods; AirPlay 2 has transient SRP, ChaCha20-Poly1305 RTSP, PTP | Not JVM | Self-reported, not independently verified |

No Android/JVM-native AirPlay **sender** library was found. Search hits were receivers or an apparently stale (~2015) repo. Absence of a hit is not proof none exists.

### Key facts and hypotheses

- **Fact (reported by pyatv docs):** a HomePod mini was verified as a RAOP target by pyatv. Hardware/firmware was v14.5, which is old.
- **Fact (reported by Music Assistant docs):** the HomePod may refuse RAOP audio unless its AirPlay access setting is "Everyone on the same network" (or the password is supplied).
- **Reported, unconfirmed:** a Roon forum post says a HomePod that is half of a stereo pair, set as a TV default speaker, or grouped may refuse third-party AirPlay 1 audio. This is exactly the owner's setup and is the main go/no-go risk. Needs a hardware test.
- **Hypothesis H1:** the pair is advertised as one logical AirPlay target (likely the group leader) that accepts RAOP and plays both channels. Unverified.
- **Hypothesis H2:** RAOP/ALAC-over-RTP with transient/legacy pairing is enough; full AirPlay 2 (PTP, HAP) is not needed for a first pass. Unverified. If H2 holds, latency is typically ~2 s (RAOP buffering; number is a general expectation, not measured here), which is acceptable for vinyl with no video.
- **Not an issue to bypass:** if a HomePod requires a password or Home-access approval, the owner must configure it; we will not circumvent authentication.

### Go/no-go (provisional)

**CONDITIONAL GO for Experiment 0 only.** There is a credible open-source sender (pyatv, MIT) that reports HomePod support, so a test is worthwhile. **No-go for scaffolding the Android app** until Gate A evidence exists. Not yet decided: Android implementation route (NDK + libraop if license permits, or a clean-room RAOP sender in Kotlin from [openairplay spec](https://openairplay.github.io/airplay-spec/)).

### Experiment 0 (smallest test, no Android code)

**Hypothesis:** the owner's HomePod stereo pair accepts a RAOP stream from a third-party sender on the LAN and plays left and right on the correct speakers.
**Why not Android first:** isolates the Apple-side risk (H1/H2) from Android build effort. It tests the pair, not the Android client. It does **not** satisfy Gate A.
**Needs real hardware:** yes (owner's pair, any computer or phone with Python on the same Wi-Fi/VLAN).

Procedure (untested; commands are a starting point):
1. In Home app: set the pair's AirPlay access to "Everyone on the same network" temporarily. Note this lowers security; revert afterwards.
2. Generate a 10 s stereo WAV: 440 Hz left only for 3 s, 880 Hz right only for 3 s, then both. Low level.
3. `pip install pyatv`, then `atvremote scan` and record which entries exist for the pair (names/IPs stay out of git).
4. `atvremote --id <id> --protocol raop stream_file=test.wav` (flags may differ by version; check `atvremote --help`).
5. Record: connects? Which speaker plays which tone? Both speakers? Startup delay; behavior with Apple TV idle vs playing; stop/restart x5.
6. Repeat targeting each individual HomePod if the pair appears as separate entries.

Outcomes: **PASS** both channels correct and repeatable; **FAIL** refused or one speaker only (then investigate AirPlay 2 pairing, see ADR-003); **INCONCLUSIVE** discovery only. Result goes in the test log.

Follow-ups if PASS: (a) check whether pyatv/libraop accept live PCM (pipe/stdin) rather than files; (b) decide Android route by licence and source review; (c) Experiment 1 on the phone.

### Unresolved risks

1. Pair acceptance of third-party senders (see above).
2. Live (non-file) input to the sender; RAOP sender wants a continuous packet clock, Android capture clock differs, so drift handling is needed.
3. libraop and AirConnect licenses unknown; GPL contamination if any GPL code is used.
4. HomePod OS changes may break older RAOP behaviour (a Roon thread mentions a recent HomePod update; unverified).
5. A clean-room Kotlin sender is real work and untested.

### Fallbacks (require explicit owner approval before pursuing, ADR-003)

- Always-on small bridge (Raspberry Pi/PC) receiving audio from Android over LAN and sending RAOP/AirPlay 2 via OwnTone or similar (GPL fine for a non-distributed personal bridge, but this changes the architecture).
- USB interface connected directly to a Pi/Mac instead of Android.
- Use an AirPlay-capable analog/line-in device as the source (this drops the Android goal).

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
| 2026-11-12 | None (desk research) | Phase 1 sender survey | UNVERIFIED, no hardware or code executed | Section 'Phase 1 findings' |

## Questions for owner

- Android phone model and version?
- Turntable model and whether it has a built-in phono preamp?
- USB audio interface model (if already owned)?
- HomePod generation and Apple TV model?
- Can HomePods be selected as an AirPlay target while Apple TV is in use?

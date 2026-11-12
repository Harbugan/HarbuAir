# AirPlay sender research (Phase 1)

Review date: 2026-11-12. Method: cloned both repositories (shallow) and read README, ROADMAP, CMake/Cargo manifests, licence files and source layout. **Nothing below was run against hardware**; "claims" are the projects' own statements, not my verification.

## Candidates

| | `akustikrausch/airplay2-sender-cpp` | `lmcgartland/airplay2-rs` |
|---|---|---|
| Commit reviewed | `fc91350` (2026-09-05) | `f824007` (2026-10-08) |
| Role | **Sender** (confirmed: `src/raop_sender.*`, demo `example/airplay_send.cpp`) | **Sender** (crates `airplay-client`, `-pairing`, `-rtsp`, `-timing`, ...) |
| Licence | Apache-2.0 (`LICENSE`, `NOTICE`); Mbed TLS 3.6.0 (Apache-2.0 option), ed25519 (zlib); logic partly ported from pyatv (MIT) - notices in `licenses/THIRD-PARTY-NOTICES.txt` | GPL-3.0-or-later (`Cargo.toml`, `LICENSE`) |
| Language / build | C++20, CMake; only dependency fetched is Mbed TLS (git, pinned by commit) | Rust workspace; README says "Alpha", "many modules still contain `todo!()` stubs", API unstable |
| Pairing | HAP PIN (Apple TV), HAP transient (HomePod/Mac), AirPlay 1 none; stored credentials as JSON | HomeKit transient (PIN 3939), other modes |
| Encryption | ChaCha20-Poly1305, X25519, SRP-6a-3072, Ed25519, HKDF-SHA512 | similar (own crates) |
| Audio | Realtime stream (type 96, UDP), **uncompressed ALAC** 44.1 kHz 16-bit stereo; input resampled linearly to 44.1 kHz | ALAC/AAC, PTP + NTP timing, multi-room |
| Timing | NTP-style timing responder (no PTP) | PTP and NTP |
| mDNS discovery | **None** (README: caller passes IP; bundled browser "still on the roadmap") | Yes (`airplay-discovery`) |
| Android evidence | **None.** Only POSIX sockets/`poll()` (`src/raop_loop.cpp`), no `__ANDROID__` code, no Android CI | **None.** Cross-compile docs target Raspberry Pi only |
| HomePod evidence | README/ROADMAP claim verified on Apple TV 4K, "a HomePod" and macOS; **upstream's Qt-free build has not had a fresh hardware pass** (their own caveat) | README: tested with HomePod Mini + Samsung TV |
| Stereo pair | Not mentioned | README: "Stereo pair members cannot be targeted individually." |
| Tests | `core_tests` (in-process fake receiver, byte-exact), `loop_tests` (loopback). Tests do **not** prove interoperability with real Apple firmware | `cargo test --workspace` (not run) |
| Security caveat | Receiver authentication is **not enforced by default** (log-and-continue); `setStrictReceiverAuth(true)` fails closed but is unproven against real receivers | not reviewed |

## Decision (ADR-002): use airplay2-sender-cpp

Reasons: permissive Apache-2.0 licence (GPL-3 would force the whole app to GPL-3 when linked/distributed); small C++ library with a sans-I/O design (`RaopIo` seam) that maps cleanly onto JNI; no heavy dependencies; already contains the Apple TV PIN and HomePod transient flows. The Rust project is a good protocol reference (PTP, groups) and is the fallback if the realtime/NTP path proves insufficient for HomePods, but is GPL and alpha.

Unverified assumptions this decision rests on (all must be tested on hardware):

1. The library's NDK build works (never built by me - no NDK reachable here, see BUILD.md).
2. `poll()`-based `RaopLoop` behaves on Android Wi-Fi, incl. inbound UDP to the sender's timing/control ports (upstream documents that the receiver sends *unsolicited inbound UDP*; Android does not firewall this by default, but Wi-Fi power-save/client isolation may).
3. Its HomePod and Apple TV recipes still work with current tvOS/HomePod software (upstream verification dates are not stated).
4. Fixed sender identity model string (`iPhone14,3`) and a random locally administered MAC are accepted by receivers.
5. Realtime ALAC at 44.1 kHz is acceptable to a HomePod stereo pair.

## Rate/format facts (from source/README)

- Input to the library: interleaved int16 stereo, any rate; non-44.1 kHz is **linearly** resampled (low quality; HarbuAir's tone and future USB capture should use 44.1 kHz to bypass it).
- On the wire: ALAC (uncompressed frames), 352 frames/packet, ChaCha20-Poly1305 per packet.
- Volume: 0-100 % mapped to -30..0 dB, 0 % = mute.

## What we did NOT evaluate

AirPlay 2 buffered audio (type 103), PTP timing (needed for tight multi-room sync), HomeKit-mode access control ("Anyone on the same network" vs. "Everyone"), and Apple's `RAOP`-level differences across tvOS versions.

## HomePod stereo pair / Apple TV routing: options to test

Nothing here is verified. Test in this order; record results in `docs/TESTING.md` and the ARCHITECTURE test log.

| Option | Expectation (hypothesis) | Why |
|---|---|---|
| A. Apple TV endpoint | Most likely to work; Apple TV renders incoming AirPlay audio on its configured output (the HomePod pair) | Upstream claims Apple TV 4K sessions work. Audio-output forwarding to the pair is common user experience but **not verified by me**. Needs on-screen PIN pairing. Access control on Apple TV (Settings > AirPlay and HomeKit) may block us |
| B. Logical pair endpoint (one HomePod name advertised for the pair) | May work; stereo pair is normally presented as one room, with a leader HomePod advertising it | HomePods ordinarily use transient pairing (no PIN). Check the `gid` / group-leader TXT fields the app displays |
| C. Individual HomePod | Expected **not** to give correct stereo (channel assignment is owned by the pair) | Rust project's README says pair members cannot be targeted individually; unverified |

The app logs raw `model`, `features`, `flags`, `gid` and group-leader TXT values for every receiver so the discovered endpoint list can be attached to the evidence log (after removing device IDs/IPs).

## Security / privacy notes

- Pairing credentials are returned by the library as opaque JSON and stored app-private (`allowBackup=false`); not logged; `DiagLog` additionally masks 32+ char hex/base64-like tokens.
- Strict receiver authentication is exposed as a UI toggle (default off, because the library says it is unproven). Recommendation: turn it on for tests; if sessions still work it can become the default.
- The audio stays on the local network; no telemetry, no microphone.

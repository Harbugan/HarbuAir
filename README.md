# HarbuAir

**Experimental Android-to-HomePod vinyl audio bridge.**

HarbuAir aims to capture stereo audio from a turntable through a USB audio interface attached to an Android phone and stream it over the local network to an existing HomePod stereo pair (currently used as Apple TV speakers).

> Status: Phase 1 proof-of-concept code exists (discovery, pairing, 440 Hz stereo test tone via a third-party AirPlay 2 sender library) but has **not been compiled or tested on hardware**. Build: docs/BUILD.md. Test plan: docs/TESTING.md. Sender research: docs/AIRPLAY_RESEARCH.md.
>
> Feasibility research / proof of concept. **AirPlay 2 sending from Android to a HomePod stereo pair is NOT yet demonstrated.** Do not claim the product works until tested on real hardware.

## Intended signal path

Turntable → phono preamp (unless built in) → stereo line-level USB audio interface → Android USB host → HarbuAir → Wi-Fi / AirPlay → HomePod stereo pair.

The Apple TV is not intended to relay audio. Whether the paired HomePods are independently addressable and can accept the proposed sender must be verified.

## First milestone: prove the difficult part

1. Inventory actual Android device, Android version, USB interface, turntable, HomePod models and network setup.
2. Research legitimate AirPlay sender implementations, supported protocol versions, licensing, pairing/authentication, and HomePod compatibility. Distinguish receivers from senders.
3. Validate discovery and connection to the **actual stereo pair**.
4. Stream a known stereo test signal from Android to the pair, measuring stability, channel separation and latency.
5. Capture live stereo audio from the USB interface and connect it to the proven sender pipeline.

Do **not** spend time on a polished UI before the AirPlay feasibility gate passes.

## Proposed stack (subject to research)

- Native Android: Kotlin, Gradle, Android Studio.
- Audio capture: Android AudioRecord / USB audio routing where supported; evaluate USB host libraries only if needed.
- Device discovery: mDNS/DNS-SD with appropriate Android permissions and network handling.
- AirPlay transport: **TBD** after source-code, license, compatibility and hardware validation. No unverified dependency is approved.
- Foreground service for user-visible audio streaming; proper lifecycle and stop controls.

## MVP, if feasible

- Detect/select external stereo audio input.
- Discover and select the HomePod stereo pair.
- Start/stop streaming with explicit connection state and actionable errors.
- Basic input level meters and clipping indication.
- Continue playback while screen is locked, with foreground notification.
- Handle unplugged USB devices, Wi-Fi changes and reconnection safely.

## Non-goals for MVP

- Streaming arbitrary Android app audio or bypassing DRM.
- Multiroom synchronization across unrelated receivers.
- Apple TV integration, HomeKit control, or cloud services.
- Guaranteed low-latency monitoring.
- Google Play publication.

## Important limitations

AirPlay 2 is not a fully open, stable public sender API on Android. HomePod authentication, pairing, timing and stereo-pair behavior may prevent a pure Android implementation. An alternative bridge architecture may be necessary; it must be explicitly approved rather than silently substituted.

The phono preamp must provide correct RIAA equalization before line-level capture. A USB-C adapter alone is not necessarily a stereo audio input.

See [ROADMAP.md](ROADMAP.md), [AGENTS.md](AGENTS.md), and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

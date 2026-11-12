# HarbuAir — instructions for coding agents

## Mission

Build a verifiable, maintainable native Android proof of concept for streaming live stereo turntable audio from a USB audio interface to the owner's existing HomePod stereo pair via AirPlay. Prioritize **technical truth over apparent progress**.

Read README.md, ROADMAP.md and docs/ARCHITECTURE.md before implementing anything.

## Non-negotiable rules

1. **Feasibility first.** Do not scaffold an elaborate UI or assert that AirPlay 2 works without a reproducible end-to-end hardware test.
2. **Research before dependency choice.** Check actual source, sender vs receiver role, maintained status, Android/ABI support, licensing, AirPlay protocol versions, HomePod stereo-pair support, authentication and security. Record links, dates, evidence and unknowns in docs/ARCHITECTURE.md.
3. Never invent test results, device support, benchmark numbers, build success, or library capabilities. Label hypotheses clearly.
4. Do not confuse AirPlay receiver projects (e.g. Shairport Sync) with sender implementations.
5. Do not bypass authentication, DRM, access controls, or copy proprietary SDK code. Prefer legitimate documented/open-source approaches with compatible licenses.
6. Keep all audio on the local network; no cloud upload, telemetry, or microphone recording unless explicitly requested. Request the minimum Android permissions.
7. Preserve stereo throughout the pipeline. No implicit mono downmix. Document sample rates, bit depth, resampling, buffering and clock synchronization.
8. Handle disconnects, audio focus, Wi-Fi changes, lifecycle, Android background restrictions and clean shutdown. Never leave recording active without clear user-visible status.
9. Make small, reviewable commits; update ROADMAP.md and the evidence log when progress changes.
10. Do not add hardware purchasing recommendations until compatibility requirements are established.

## Workflow

**Research → minimal experiment → build → hardware test → document evidence → refine → repeat → release.**

For every task:
- State the specific hypothesis and acceptance criteria.
- Identify whether it can be tested in CI/emulator or requires the owner's real phone/HomePods.
- Implement the smallest useful change.
- Run available static checks, unit tests and build commands; report exact commands and results.
- If hardware is unavailable, provide a reproducible manual test procedure and mark the milestone BLOCKED/UNVERIFIED, never PASSED.
- Record decisions and limitations; do not hide failures behind mock data.

## Initial assignment for Codex

**Do not start by building the full app.** First produce a feasibility investigation:
1. Compare credible AirPlay sender libraries/protocol implementations suitable for Android; inspect actual code and license.
2. Determine whether each can address a HomePod stereo pair associated with Apple TV and what pairing/authentication is needed.
3. Propose the smallest on-device test that sends a short, original/generated stereo test signal to the pair.
4. Document a go/no-go decision, unresolved risks and fallback options.
5. Only then scaffold the minimal Kotlin Android project if a credible test path exists.

## Project conventions

- Kotlin, Gradle Kotlin DSL, clear package boundaries; avoid unnecessary frameworks.
- No secrets, credentials, pairing tokens, device identifiers or private IPs in git.
- Provide a .gitignore before committing Android project files.
- Keep docs in English for tool interoperability; UI copy may be decided later.
- Prefer explicit error states over silent retries.
- APK output should eventually be reproducible through CI, but CI cannot validate real AirPlay hardware.

## Definition of done for feasibility

A documented real-device demonstration that plays a generated **stereo** signal from Android on the intended HomePod stereo pair, with repeatable connect/disconnect behavior. Anything less is research progress, not validated streaming.

## Phase 1 layout (keep in sync)

Kotlin packages under `app/src/main/java/dk/harbuair/`: `ui`, `discovery`, `engine`, `audio`, `diag`. Native JNI engine in `app/src/main/cpp/`. AirPlay sender: git submodule `third_party/airplay2-sender-cpp` (Apache-2.0, pinned commit; update docs/AIRPLAY_RESEARCH.md when bumping). New audio inputs implement `audio/AudioSource`; do not change the engine for them. Never log pairing material; credentials live only in `CredentialStore`. No hardware result may be marked PASSED without an entry in docs/TESTING.md / the evidence log.

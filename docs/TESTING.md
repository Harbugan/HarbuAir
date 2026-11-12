# Testing

## Automated (no hardware)

| Test | Command | Status |
|---|---|---|
| Tone generator, channel routing, level, frequency, discovery model parsing, log redaction | `./gradlew :app:testDebugUnitTest` | NOT RUN (no Android toolchain in authoring environment) |
| Upstream sender protocol tests (fake receiver) | see BUILD.md host build | NOT RUN by us |

These cannot show AirPlay compatibility.

## Manual hardware test - Xperia XZ1 Compact (LineageOS 22.2, Android 15)

Equipment: phone and Apple TV/HomePods on the **same** Wi-Fi/subnet (no guest network, no AP client isolation, VPN off).

**Safety:** lower the HomePod/Apple TV volume first. The tone is -20 dBFS and the app sets receiver volume to 20 % after the session starts, but receiver gain behaviour is untested.

1. Install the debug APK (BUILD.md). Open the app; no permission prompts are expected.
2. **T1 Discovery** - Within ~10 s the list should show the Apple TV and HomePods. Record, per entry: name, type, model, port, `gid`, group-leader flag. PASS = expected devices listed with IPv4 address. (Remove IPs/device IDs before pasting anywhere public.)
3. **T2 Option A - Apple TV.** Select the Apple TV, auth "AirPlay 2 PIN". Start tone. Enter the PIN shown on the TV. PASS = Pairing "credentials stored" and State STREAMING, tone audible on **both** HomePods.
4. **T3 Channel check.** Stop; choose "Left only", start. PASS = sound only from the left HomePod. Repeat "Right only". If both play the same speaker, stereo is NOT verified.
5. **T4 Option B - pair endpoint.** Select the HomePod entry for the pair, auth "transient". Same checks as T2/T3. Note whether one or two entries appear for the pair.
6. **T5 Option C - single HomePod.** Expected not to produce correct stereo; record behaviour.
7. **T6 Lifecycle.** Stop/Start 5 times; press Home while streaming (must stop and show IDLE when returning); switch Wi-Fi off/on while streaming (expect an error state, not a hang); let it run 10 minutes.
8. **T7 Reconnect.** After T2, tap "Forget pairing" and repeat; then without forgetting - second run should skip the PIN.

Collect: the diagnostics pane (credentials are never logged; check the pane for anything sensitive before sharing), `adb logcat -s HarbuAir HarbuAirNative`, device/OS versions of the Apple TV and HomePods.

### Troubleshooting

- *"Timed out waiting for the device"* at connect: wrong auth mode for the device, or the receiver cannot send UDP to the phone (client isolation, Wi-Fi power-save). Try the other auth mode.
- Connected but silent: record exact logs; upstream documents this symptom class (see their README "recipe").
- No devices listed: check same subnet, multicast filtering on the router (mDNS), and that location/"Nearby devices" permissions are not required (they are not requested).

## Result log

Nothing has been tested on hardware. All hardware milestones: **UNVERIFIED**.

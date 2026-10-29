# Ring Pong Wear OS

A small, single-screen circular Pong-style game written in Kotlin using only the Android framework. Intended for round Wear OS watches such as the Samsung Galaxy Watch5 Pro.

## Game

- Black background and round-screen-aware Canvas rendering.
- A luminous arc paddle sits near the circular wall. Keep it aligned with the ball's impact angle to bounce the ball back inward.
- Each successful return increases the score and gradually raises the ball speed.
- Best score is saved locally on the watch.
- Rotate the Watch5 Pro's digital bezel to move the paddle. Touching/dragging the outer ring is provided as a fallback.
- The display stays on while the game is open.

## Rotary input notes

Watch5 Pro uses a **digital bezel**, not a physical rotating crown. Wear OS exposes this through standard Android rotary input events; the game reads `MotionEvent.ACTION_SCROLL` / `MotionEvent.AXIS_SCROLL` (and checks `SOURCE_ROTARY_ENCODER`). It does not use the old Tizen `rotarydetent` API or a Samsung-only Tizen API.

If rotation direction feels reversed on a specific firmware, edit `ROTARY_DIRECTION` in `RingPongView.kt` from `1f` to `-1f`. If movement is too fast or too slow, tune `ROTARY_RADIANS_PER_UNIT` (default `0.12f`).

## Back gesture behavior

- `android:windowSwipeToDismiss=false` disables Wear OS's activity swipe-to-dismiss gesture for this full-screen game.
- `OnBackInvokedDispatcher.PRIORITY_OVERLAY` consumes the software Back gesture on API 33+, with `onBackPressed()` as the API 30–32 fallback.
- The OS power button / return-to-watch-face behavior remains system-owned and cannot be blocked by an ordinary app.

## Build from GitHub Actions

1. Create a GitHub repository and upload the contents of this folder to its root (the `.github/workflows/build-apk.yml` file must remain in place).
2. Push to `main` or `master`, or open **Actions → Build Ring Pong Wear OS APK → Run workflow**.
3. Open the completed workflow run and download the `ring-pong-wear-debug-apk` artifact. It contains `app-debug.apk`.
4. Install the APK on the watch with your usual Wear OS ADB / wireless debugging workflow. The debug package ID is `com.example.ringpongwear.debug`.

The workflow intentionally uses `android-actions/setup-android@v4` with `packages: ''` and installs `platforms;android-35`, `build-tools;35.0.0`, and `platform-tools` explicitly. Do **not** add the deprecated SDK package named `tools`; current Android SDK repositories no longer provide it.

## Build locally

Open the folder in a recent Android Studio release and allow Gradle sync; the project uses Android Gradle Plugin `8.7.3`, Gradle `8.9`, Kotlin `2.2.21`, JDK 17, compile SDK 35 and target SDK 34. This source bundle does not include the binary Gradle wrapper JAR; the included GitHub Actions workflow provisions Gradle directly and is the reproducible build path. To build from a terminal, install Gradle 8.9 and Android SDK platform 35/build-tools 35.0.0, then run:

```bash
gradle --no-daemon clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Verification status

The source bundle is statically checked for project structure, XML well-formedness, workflow configuration, back-handling declarations, and rotary-event handling. This environment has no Android SDK or Gradle installation and cannot access the network, so an actual Gradle compile, GitHub Actions run, and physical Watch5 Pro input test could not be performed here. The included workflow performs an APK-existence check after the build; that is the definitive compile check once pushed to GitHub.

## References

- [Android Developers: Rotary input with Compose for Wear OS](https://developer.android.com/training/wearables/compose/rotary-input) — documents standard Wear OS rotary input concepts. This project uses the equivalent Android `MotionEvent` scroll axis in a custom Canvas view.
- [Android Developers: Exit full-screen activities on Wear](https://developer.android.com/training/wearables/views/exit) — documents Wear swipe-to-dismiss and the `android:windowSwipeToDismiss` theme switch.
- [android-actions/setup-android](https://github.com/android-actions/setup-android) — SDK command-line tool setup and the retired `tools` package issue.
- [CaYatur/Orbo_WearOSGames](https://github.com/CaYatur/Orbo_WearOSGames) — an existing Wear OS Canvas-game project used as a high-level reference for lightweight rendering and round-screen-aware game design; no code was copied from it.

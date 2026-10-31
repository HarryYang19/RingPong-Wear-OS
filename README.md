# Ring Pong Wear OS 1.2.0

A small, single-screen circular Pong-style game written in Kotlin using the Android framework. Intended for round Wear OS watches such as the Samsung Galaxy Watch5 Pro.

## Game and controls

- Black background, a clean circular arena outline, a thick white arc paddle and a slightly larger solid-white ball.
- Rotate the Watch5 Pro digital bezel to move the paddle. Rotary input direction remains inverted (`ROTARY_DIRECTION = -1f`) to match the corrected direction from the previous build.
- Paddle motion is driven by angular momentum impulses. While the bezel is turning, impulses accelerate the paddle; after turning stops, it coasts briefly **in the same direction** and slows through drag. There is no spring pulling it backward and no elastic recoil.
- Touch input never steers the paddle. The outer 30% radial annulus is treated as a virtual-bezel touch zone and is consumed without action. Only a tap in the inner 70% of the arena can start/restart the game.
- Each successful return increases the score and gradually raises ball speed. The acceleration rate tapers after 20 points and again after 50 points so longer rallies remain more achievable.
- Best score is stored locally on the watch. During play, the score is the only text shown; title, instructions and best-score text are hidden.
- A short, low-amplitude haptic tick confirms each successful paddle collision. Vibration is optional and never blocks gameplay.
- The display stays on while the game is open.

## Speed progression

The first 20 successful returns retain the previous opening pace (`1.035x` per return). From score 21 through 50 the multiplier becomes `1.008x`; after 50 it becomes `1.0045x`. The speed cap remains in place as a final safety limit.

## Rotary input and forward inertia

Watch5 Pro uses a **digital bezel**, not a physical rotating crown. Wear OS exposes this through standard Android rotary input events; the game reads `MotionEvent.ACTION_SCROLL` / `MotionEvent.AXIS_SCROLL` and checks for rotary-encoder input. It does not use the old Tizen `rotarydetent` API.

Tuning constants are in `RingPongView.kt`:

- `ROTARY_DIRECTION`: rotary direction (`-1f` for the corrected direction).
- `ROTARY_RADIANS_PER_UNIT`: base sensitivity per rotary unit.
- `ROTARY_INERTIA_GAIN` (12.5): angular impulse added for each rotary unit.
- `PADDLE_DRAG` (11.5): how quickly forward momentum decays. Higher values shorten the coast; the paddle never snaps backward.
- `MAX_PADDLE_ANGULAR_SPEED`: maximum angular speed.

## Back gesture behavior

- `android:windowSwipeToDismiss=false` disables Wear OS's activity swipe-to-dismiss gesture for this full-screen game.
- `OnBackInvokedDispatcher.PRIORITY_OVERLAY` consumes the software Back gesture on API 33+, with `onBackPressed()` as the API 30–32 fallback.
- The OS power button / return-to-watch-face behavior remains system-owned and cannot be blocked by an ordinary app.

## Build from GitHub Actions

1. Create a GitHub repository and upload the contents of this folder to its root (the `.github/workflows/build-apk.yml` file must remain in place).
2. Push to `main` or `master`, or open **Actions → Build Ring Pong Wear OS APK → Run workflow**.
3. Open the completed workflow run and download the `ring-pong-wear-debug-apk` artifact. It contains `app-debug.apk`.
4. Install the APK on the watch with your usual Wear OS ADB / wireless debugging workflow. The debug package ID is `com.example.ringpongwear.debug`.

The workflow uses `android-actions/setup-android@v4` and installs Android SDK Platform 35, Build Tools 35.0.0 and Platform Tools explicitly. It does not request the retired SDK package named `tools`.

## Build locally

Open the folder in a recent Android Studio release and allow Gradle sync; the project uses Android Gradle Plugin `8.7.3`, Gradle `8.9`, Kotlin `2.2.21`, JDK 17, compile SDK 35 and target SDK 34. This source bundle does not include the binary Gradle wrapper JAR; the included GitHub Actions workflow provisions Gradle directly and is the reproducible build path. To build from a terminal, install Gradle 8.9 and Android SDK platform 35/build-tools 35.0.0, then run:

```bash
gradle --no-daemon clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Verification status

Static source and archive checks are included in this release process. This environment has no Android SDK or Gradle installation and no connected Galaxy Watch5 Pro, so an actual Android Gradle build and physical rotary/touch/haptics test cannot be certified here. Run the included GitHub Actions workflow for the definitive APK compile check, then test rotary feel on the target watch.

## References

- [Android Developers: Rotary input with Compose for Wear OS](https://developer.android.com/training/wearables/compose/rotary-input) — Wear OS rotary input concepts. This project uses the equivalent Android `MotionEvent` scroll axis in a custom Canvas view.
- [Android Developers: Exit full-screen activities on Wear](https://developer.android.com/training/wearables/views/exit) — Wear swipe-to-dismiss and `android:windowSwipeToDismiss`.
- [android-actions/setup-android](https://github.com/android-actions/setup-android) — SDK command-line tool setup.

# Project checks and known limits

## Static checks expected in the source bundle

- Manifest and style XML parse successfully.
- Activity is exported with a launcher intent filter and watch-only feature declaration.
- Minimum SDK is 30; Java/Kotlin bytecode target is 17.
- Back handling includes both Wear swipe-to-dismiss theme configuration and Android Back callback handling.
- Gameplay reads `AXIS_SCROLL` from generic motion input and has touch fallback.
- Ball speed growth is capped, the ball is kept inside the arena after a hit, and best score is persisted.
- CI explicitly installs Android SDK packages without the retired `tools` package.

## Not claimed as tested here

No Android SDK, Gradle installation, network access, emulator, or Galaxy Watch5 Pro is available in the authoring environment. Therefore the APK cannot be claimed as already compiled or hardware-verified. Run the included GitHub Actions workflow to confirm compilation, then test rotary direction/sensitivity and the Wear OS system-gesture behavior on the actual watch.
- Kotlin Gradle Plugin 2.2.21 is selected to fit the documented Gradle 8.9 / AGP 8.7.3 compatibility range, rather than using an older Kotlin plugin outside its fully-supported AGP range.

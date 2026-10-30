# Project checks — Ring Pong Wear OS 1.1.0

## Static checks to run before publishing

- Parse every Android XML resource/manifest file and verify the haptics permission is present.
- Check the GitHub Actions workflow has a valid structure and explicitly installs Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools, without requesting the retired `tools` package.
- Verify source declarations for rotary `AXIS_SCROLL` input, the reversed direction constant, target-angle spring smoothing, fixed physics substeps, decaying ball curve, inner-face paddle collision, gentle vibration, local high-score storage, and the back-dismiss configuration remain present.
- Run the Kotlin compiler against minimal local Android API stubs to catch Kotlin syntax and local type-flow issues (this is not a substitute for compiling against the real Android SDK).
- Verify the ZIP can be read completely and includes the full project at `RingPongWear/`.
- Incremented app metadata to version 1.1.0 / versionCode 2.

## Version 1.1.0 changes

- Reversed rotary input direction (`ROTARY_DIRECTION = -1f`).
- Added a damped spring for paddle following, with angular-speed limiting.
- Added 120 Hz maximum physics substeps and a subtle decaying ball curve after collisions.
- Enlarged the arena, paddle stroke and HUD; switched to a monochrome black/white palette.
- Moved collision detection to the ball touching the paddle's inner face and included rounded-end tolerance, reducing visible clipping/tunnelling.
- Added a short low-amplitude vibration when a paddle return succeeds, with a device-tuned tick fallback when amplitude control is unavailable.

## Not claimed as tested here

This environment has a JDK but no Android SDK, Android platform jar, Gradle installation, emulator or connected Galaxy Watch5 Pro. The Kotlin/Android build therefore cannot be certified as compiled or hardware-tested in this session. Static checks and offline physics/geometry checks can be performed, but the included GitHub Actions build and a real-watch test remain the final verification for compilation, Samsung rotary direction, perceived haptics and system-gesture behavior.

# Project checks — Ring Pong Wear OS 1.2.1

## Static checks

- Parse Android XML resources and manifest; confirm vibration permission and round-watch fullscreen configuration remain present.
- Parse GitHub Actions workflow and confirm explicit Platform 35 / Build Tools 35.0.0 / Platform Tools install without requesting the retired `tools` package.
- Verify the rotary input path, unchanged corrected direction (`ROTARY_DIRECTION = -1f`), forward-only angular impulse + drag model (`ROTARY_INERTIA_GAIN = 14.0f`, `PADDLE_DRAG = 12.0f`), and absence of the old spring target/recoil model.
- Verify no touch handler steers the paddle; taps only start/restart inside the central 70% radius, and the outer virtual-bezel annulus is consumed.
- Verify speed multipliers: 1.035 through score 20, 1.008 through score 50, and 1.0045 after 50.
- Verify edge ticks are removed, the ball is drawn as a single solid white disk at the increased radius, and PLAYING HUD draws score text only.
- Check the fixed 120 Hz maximum physics substep, decaying ball curve, inner-face paddle collision, separate soft virtual-bezel haptic and collision haptic, local high-score storage, and back-dismiss configuration.
- Verify ZIP integrity and that the archive contains the full project rooted at `RingPongWear/`.

## Version 1.2.0 changes

1. Reduced speed growth after 20 points and tapered it again after 50 points to make 50+ rallies more attainable.
2. Replaced spring-following paddle motion with rotary angular impulses and exponential drag. The paddle briefly coasts forward in the direction of the last bezel turn and never springs backward.
3. Removed touch steering. The outer virtual-bezel annulus is ignored; only inner-area taps start or restart the game.
4. Removed arena edge ticks, enlarged and simplified the ball to a solid-white disk, and hid all non-score text during active play.
5. Incremented app metadata to version 1.2.0 / versionCode 3.

## Verification limits

There is no Android SDK, Android platform jar, Gradle installation, emulator or connected Galaxy Watch5 Pro in this environment. Static checks cannot certify a real Android build or hardware feel. The GitHub Actions workflow builds and verifies the APK when run in the user's repository; actual bezel response and haptic strength still require a watch test.


## Version 1.2.1 changes

1. Added one soft haptic tick on `ACTION_DOWN` in the outer virtual-bezel touch annulus only; movement events do not repeat the vibration, and touch still never steers the paddle.
2. Kept the existing collision haptic unchanged (12 ms / amplitude 38) and set the virtual-bezel tick to 8 ms / amplitude 22 when amplitude control is available.
3. Increased rotary impulse gain from 12.5 to 14.0 and the angular-speed cap from 11.5 to 13.0; drag increases slightly from 11.5 to 12.0 to retain controlled coast. The corrected direction remains `-1f`.
4. Incremented app metadata to version 1.2.1 / versionCode 4.

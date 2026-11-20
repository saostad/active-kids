<img src="assets/icon.svg" width="64" height="64" alt="">

# Active Kids

A home-made, camera-based active-play console. An Android phone (built for a Pixel 8 Pro) tracks
players' bodies with MediaPipe and shows games on the TV over USB-C → HDMI, while the phone itself
becomes the parent control panel. ESP32 accessories (wristbands, hit pads, LED targets) come next.

Everything runs on the phone. Video is never saved or uploaded.

## What works now

- **Body tracking**: CameraX feeds MediaPipe Pose Landmarker (lite/full models, GPU with CPU fallback),
  up to 4 players. Each person keeps a stable colour and score slot.
- **Ultrawide camera**: "Rear, widest" picks the shortest-focal-length rear camera and zooms to its
  minimum (below 1× on Pixels). If the phone doesn't offer that to apps, a visible notice says so.
- **TV output**: when an external display appears, the game opens full screen on it (Android
  `Presentation`) and the phone shows controls plus a live preview. Without a TV, the game runs on
  the phone, with a "Full screen" button.
- **Bubble Pop**: a countdown, then bubbles float up for players to pop with their hands. Per-player
  scores, a timer, a results screen and synthesized sound effects.
- **Star Reach**: touch stars in your player color. Targets move to a new spot after each hit and
  are scaled to each player's body.
- **Pose Match**: copy the pose shown on screen and hold it until your progress bar fills. Each
  player can score once per prompt.
- **Trail Dash**: a solo three-lane runner. Stand centered during the countdown, then step sideways
  to change lanes or hop over logs. Dodge boulders by changing lanes. Each obstacle cleared earns a
  point.
- **Freeze Dance**: dance to a synthesized beat, then hold still when the music stops. Up to four players.
- **Penguin Parade**: lean with both feet planted to collect fish and avoid snowbanks. Solo.
- **Meteor Shield**: block meteors with your hands. Two players each defend a side of the moon base.
- **Robot Orchestra**: tap drums and melody pads in time with falling notes. Play solo or as a duet.
- **Shape Gate**: fit the pose outline before the gate arrives. In co-op, both players must fit their halves.
- **Treasure Team**: hold the lever while a teammate grabs the treasure, then swap roles. Two players.
- **Mirror Leader**: record and copy three poses to a beat, then rotate the leader. Two to four players.
- **Round goals**: every game has a visible score target, a timer, and win and loss screens.
  Trail Dash, Penguin Parade, and Meteor Shield also end when the mistake limit is reached.
- **Settings page**: every tunable (camera, model, confidences, players, round length, bubble
  rate/size/speed, hand reach, brightness, skeleton, mirror, sound) with ranges and validation.
- **Visible errors and fallbacks**: problems show as notices on the phone, and fallbacks also show as
  badges on the TV. They're logged under the `ActiveKids` tag; fallbacks carry `fallback=true`.

## Build and install

Requirements: the Android SDK at `~/Android/Sdk` (platform 36) and JDK 21 (pinned in `mise.toml`;
run `mise install`).

```sh
./gradlew testDebugUnitTest      # unit tests (game rules, player tracking, transforms, settings)
./gradlew assembleDebug          # first build downloads the MediaPipe models (~15 MB)
./gradlew lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To run just the desktop playability simulations, use
`mise exec -- ./gradlew testDebugUnitTest --tests '*PlayabilitySimulationTest'`.
They advance the game rules with simulated reaction delays, irregular frame intervals, and missed
moves. They check a route to a win for each game and cover the loss conditions.

On the phone: Settings → About phone → tap **Build number** 7× → Developer options → **USB debugging**.

## GitHub releases

Pushing a `v*` tag runs the tests and lint, signs the release APK, and publishes it with a
SHA-256 checksum on GitHub Releases. The workflow checks the signing certificate against the
original sideloaded builds so it can update them in place. GitHub Actions uses the
`APK_SIGNING_KEYSTORE_BASE64` and `APK_SIGNING_KEYSTORE_PASSWORD` repository secrets.

## Hardware setup

1. Mount the phone in landscape under or over the TV, rear camera facing the room. Players stand
   about 2–3 m back.
2. Connect a **USB-C → HDMI adapter with power pass-through** and keep the phone charging, because
   tracking keeps it busy.
3. If the TV shows nothing, update the phone. Some builds also need a display-output toggle under
   Developer options.

## Code map

| Path | What |
|---|---|
| `pose/CameraController.kt` | CameraX setup, widest-lens selection, frame rotation |
| `pose/PoseTracker.kt` | MediaPipe live-stream wrapper, GPU→CPU fallback |
| `pose/PlayerTracker.kt` | Stable player slots across frames |
| `game/GameEngine.kt` | Per-display-frame loop shared by TV and phone views |
| `game/GameView.kt` | Draws camera, skeletons, game; letterboxed 1920×1080 game space |
| `game/bubblepop/` | `BubblePopLogic` (pure, tested) + `BubblePopGame` (drawing, sound) |
| `game/motion/` | Motion game rules, drawing, and shared round controls |
| `display/TvDisplayController.kt` | External display detection and `Presentation` |
| `settings/` | `AppSettings` + specs/validation, SharedPreferences repository |

To add a game, implement `Game`, add an entry to `GameKind`, and put the rules in a pure class with
tests.

## Next

- ESP32 Bluetooth accessories: a shared message format, multi-device connect and auto-reconnect.

# Shadow Mission

A 2D side-view stealth game for Android: slip into guarded buildings, take out one target and get out unseen.
Built with Kotlin and [libGDX](https://libgdx.com).

## Install on your phone

Every push to `main` builds an APK. Open the **Latest build** release on your phone, download
`shadow-mission.apk` and install it (allow installs from your browser when Android asks).

## Project layout

| Module | What it holds |
| --- | --- |
| `core` | All game logic and rendering, platform independent, with unit tests |
| `android` | Android launcher and APK build |
| `desktop` | Desktop launcher for quick testing without a phone |
| `assets` | Levels (`assets/levels/*.json`) and later art and sound |

## Build locally

```
./gradlew test                 # unit tests
./gradlew :desktop:run         # play on desktop
./gradlew :android:assembleDebug   # APK (needs an Android SDK)
```

`./gradlew :desktop:run --args="--screenshot out.png"` renders one frame to a PNG and quits.

## Levels

Levels are JSON files describing a building in cross-section: floors made of side-by-side rooms,
doors in the walls between rooms, stairs between floors, and spawn points for the assassin,
guards, the target and the exit. `LevelValidator` checks every file in `assets/levels` during `./gradlew test`.

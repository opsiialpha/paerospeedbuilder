# PaeroBuilder (NeoForge 1.21.1)

Practice speed building offline: build previews, countdown, accuracy check, stopwatch and a finish screen.
Requires **WorldEdit for NeoForge 1.21.1** (7.3.5+).

## Build
Needs JDK 21. The builds are in `builds.zip`; unzip them first:

    unzip -q builds.zip -d src/main/resources/assets/paerospeedbuilder/
    ./gradlew build          (Windows: gradlew.bat build)

The jar ends up in `build/libs/paerobuilder-1.3.1.jar`. Test with `./gradlew runClient`.

## Commands
- `/pb <build>` - preview, countdown, then build (enters build mode, leaves it when you finish)
- `/pb setup` - builds a 15x15 platform under your feet and puts the 7x16x7 build area on top of it, centred on you (16 blocks of air above, no teleport). If a setup already exists it tells you to use `/pb resetup`
- `/pb resetup` - deletes the existing setup and builds a new one where you are standing
- `/pb area` - shows the build area and its coordinates
- `/pb grind <build>`, `/pb view <build>`, `/pb restart`, `/pb reset`, `/pb stop`
- `/pb create|list|delete` - custom builds
- `/pb time <build>` - best time
- `/pb config` - settings (also in Mods -> PaeroBuilder -> Config)
- `/pb finish text|color|test|reset` - finish title

## Config
- Build Preview / Preview Time
- Finish Title Time (1-60 s, default 5) - `/pb config finishtime <seconds>`
- Difficulty - `/pb config difficulty default|paero`
  - **Default**: starting a build clears your inventory and gives you the blocks
  - **Paero**: your inventory is never touched (nothing cleared, given, used up or refunded)
- UI Theme - `/pb config theme default|dark|paero`
  - **Default**: normal colours; every countdown number has its own colour (5 green, 4 aqua, 3 yellow, 2 orange, 1 red)
  - **Dark**: same, but yellow chat text becomes light gray
  - **Paero**: everything in white-to-gray, the countdown fades from dark gray (5) to white (1) and BUILD! is a white-to-gray gradient
  - The finish title, finish time and stopwatch colours are yours to choose and are never changed by a theme

## Build mode
`/pb <build>` turns build mode on and it turns off when you finish. Your game mode is never changed.

## Credits
Rebuilt by [@paeromc](https://identiq.in/u/paeromc) using Claude.

Follow me: https://identiq.in/u/paeromc

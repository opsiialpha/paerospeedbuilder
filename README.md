# Paero's SpeedBuilder (NeoForge 1.21.1)

Practice speed building offline: build previews, countdown, accuracy check, stopwatch and a finish screen.
Requires **WorldEdit for NeoForge 1.21.1** (7.3.5+).

## Build
Needs JDK 21. The builds are in `builds.zip`; unzip them first:

    unzip -q builds.zip -d src/main/resources/assets/paerospeedbuilder/
    ./gradlew build          (Windows: gradlew.bat build)

The jar ends up in `build/libs/paerospeedbuilder-1.3.0.jar`. Test with `./gradlew runClient`.

## Commands
- `/sb <build>` - preview, countdown, then build (enters build mode, leaves it when you finish)
- `/sb area` - 15x15 platform under your feet + 7 blocks of air above
- `/sb grind <build>`, `/sb view <build>`, `/sb restart`, `/sb reset`, `/sb stop`
- `/sb create|list|delete` - custom builds
- `/sb time <build>` - best time
- `/sb config` - settings (also in Mods -> Paero's SpeedBuilder -> Config)
- `/sb finish text|color|test|reset` - finish title

## Config
- Build Preview / Preview Time
- Finish Title Time (1-60 s, default 5) - `/sb config finishtime <seconds>`
- Difficulty - `/sb config difficulty default|paero`
  - **Default**: starting a build clears your inventory and gives you the blocks
  - **Paero**: your inventory is never touched (nothing cleared, given, used up or refunded)

# NexTV Radio integration status

Original NexTV radio source is available in the supplied nextv-main.zip. This branch enables Kotlin and Compose but the Java RadioActivity remains the active screen until the original Compose route is fully wired and builds.

## Required integration

- Port RadioScreen.kt, RadioFocus.kt, RadioUi.kt, RadioMapper.kt
- Port RadioRepository.kt, RadioPayload.kt, RadioCache.kt, RadioPrefs.kt, RadioPlayer.kt, Media3RadioEngine.kt
- Supply image loader and NexMark/formatters dependencies or replace their imports with local equivalents
- Create Compose activity to own the player, repository, preferences and lifecycle; register in manifest and route Glass TV Radio navigation to it
- Remove news, sports, weather and prayer cards, preserve stations, Now Playing, favourites and recent stations; no search
- Preserve DPAD center long press favourite and ensure key-up does not accidentally start playback
- Build assembleDebug and validate with Android TV remote before release

Do not ship or describe the Java RadioActivity as the native NexTV interface. No build verification has been performed for this integration.

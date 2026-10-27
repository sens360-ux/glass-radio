# NexTV Radio integration in Glass TV

This feature branch preserves the Glass TV launcher and routes its Radio shortcut to `NexRadioActivity`, which hosts `NexTvRadioScreen`.

## Integrated on this branch

- [x] Ported NexTV's three-column radio-only visual structure: Radio Stations, animated Now Playing, Recently Played and Favourite Stations
- [x] Ported NexTV's `RadioFocus` zone-navigation rules to `NexTvRadioFocus.kt`
- [x] Recreated original focus outlines, glass panels, station logos/initials, playback buttons and animated equaliser
- [x] Short OK plays the highlighted station; long-press OK or Menu toggles its favourite without starting playback
- [x] D-pad navigation, media next/previous, channel +/- and media play/pause
- [x] Media3 ExoPlayer with audio focus, noisy-audio handling and metadata callbacks
- [x] Radio Browser Malaysian directory with mirror failover and local JSON cache
- [x] Persisted favourites/recent history and migration from the previous Java radio station-name keys
- [x] Removed search, news, sports, prayer and weather cards from the radio screen
- [x] Added JVM tests for remote-focus transitions and configured GitHub Actions to run tests before APK assembly

## Outstanding validation and limitations

- [ ] Confirm a successful `testDebugUnitTest assembleDebug` GitHub Actions run. **Not yet verified**: the connector's combined commit statuses and PR-triggered workflow-run query did not return a run for the latest push. This does **not** establish a pass or failure.
- [ ] Download the produced APK and test on a real Android TV with a D-pad remote, including long-press behaviour and several station formats.
- [ ] Compare on-device visuals to the original NexTV screen. The layout is a source-based port, not a pixel-perfect verified copy; the original `NexMark` asset and authenticated Hub backend are not bundled.
- [ ] Confirm player lifetime policy when returning Home. The current ExoPlayer is Activity-owned and releases on exit; original NexTV kept its app-scoped player running on Home.
- [ ] Confirm stream failover and playback error behaviour on actual Malaysian radio stations.

## Source and attribution

Visual and focus implementation adapted from NexTV `ui/radio/RadioScreen.kt`, `RadioFocus.kt`, `RadioUi.kt` (MIT, copyright 2026 Nexdata). License: `licenses/NEXTV-MIT-LICENSE.txt`. The NexTV authenticated Hub, news and sports services were deliberately not copied into Glass TV.

## Build

GitHub Actions: `.github/workflows/build-apk.yml` on `feature/nextv-radio-integration`, artifact `GlassTV-Premium-Radio-APK` on success. Local Gradle build requires Android SDK and Gradle wrapper downloads.

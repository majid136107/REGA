# REGA — مسیریاب هوشمند

Android navigation prototype migrated from WAY 0.4.0 and rebranded as REGA 1.0.0.

## Included in this REGA update

- New application identity: `com.rega.navigation`, app name REGA, version 1.0.0.
- The approved REGA road-and-location app artwork is installed as the launcher icon.
- Search results are presented for selection instead of silently choosing the first result.
- Nominatim search is biased toward the current coordinates while remaining restricted to Iran.
- Location updates request all enabled providers and handle devices without an available provider more safely.
- Existing Persian, English, Arabic, Turkish, Sorani Kurdish, and Kurmanji Kurdish UI, OpenStreetMap map, OSRM car routing, and route-step text-to-speech are retained.

## Important limitations still to address

This is not yet a production or live turn-by-turn navigation app. It calculates a route and can read route steps aloud, but does not continuously track route progress, detect off-route movement, or automatically reroute. Offline maps/routing, traffic, road closures, speed limits, lane guidance, route alternatives UI, and production-grade map/geocoding/routing services are not implemented.

The prototype uses public OpenStreetMap tiles, Nominatim, and the OSRM demo service. These services have usage policies, rate limits, and no production uptime guarantee. Do not treat this build as a guaranteed navigation service.

## Build on GitHub Actions

1. Upload the contents of this folder to a GitHub repository.
2. Open **Actions** and run **Build REGA APK**, or push to `main`/`master`.
3. Download the `REGA-debug-APK` artifact from the completed workflow.

The workflow builds a debug/test APK with JDK 17, Android SDK 35, and Gradle 8.9. This is not a Play Store release build.

## Build validation status

The source was updated and statically inspected in this workspace. Android SDK and Gradle are not installed here, so a real Gradle compile and device test have not yet been performed. The GitHub Actions build must pass before installing the APK.

## Technical details

- Kotlin, Jetpack Compose, Android Gradle Plugin 8.7.3, Kotlin 2.0.21
- `applicationId`: `com.rega.navigation`
- `minSdk`: 24; `targetSdk`: 35; `versionCode`: 5; `versionName`: `1.0.0`

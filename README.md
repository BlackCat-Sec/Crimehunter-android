# Skyline Stick Clash

Skyline Stick Clash is now a native Kotlin Android stickman fighting game built for Play Store packaging.

## What is in the app

- Fullscreen landscape gameplay with a custom Android `View`
- Original 2D stickman arena combat with touch controls
- Enemy AI, combo-driven attacks, jumps, dashes, and special bursts
- Round progression with upgrade picks between matches
- Mobile HUD, result overlays, and menu screens built in Kotlin
- Debug APK and release AAB builds through Gradle

## Build

From the repo root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat bundleRelease
```

## Outputs

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release bundle: `app/build/outputs/bundle/release/app-release.aab`

## Notes

- The app no longer depends on the old WebView wrapper or the extracted browser game assets.
- The current package name is `com.gowda.crimehunter`.
- Before Play Store upload, create and use your own signing key for the release bundle.

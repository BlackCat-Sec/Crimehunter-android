# Crimehunter

Crimehunter is now a native Kotlin Android sniper-action game built for Play Store packaging.

## What is in the app

- Fullscreen landscape gameplay with a custom Android `View`
- Rooftop sniper missions with drag-to-aim shooting
- Moving enemies, civilian fail states, and reload windows
- Level progression with mission rewards and upgrade cards
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

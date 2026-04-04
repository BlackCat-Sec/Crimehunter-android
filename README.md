# Crimehunter Android Wrapper

This repository turns the provided web game bundle into an Android Kotlin app that is structured for Google Play submission.

## What is included

- Kotlin Android app using a fullscreen `WebView`
- Local asset hosting through `WebViewAssetLoader`
- Android-safe Poki bridge stubs so the Unity runtime can boot without the original ad SDK
- Release build configuration with shrinking and ProGuard enabled
- A small extraction script that pulls the Unity runtime out of the original `code.txt`

## Important blocker

The provided `code.txt` contains the Unity JavaScript runtime, but it does **not** include the actual WebAssembly game binary:

- Required file: `app/src/main/assets/web/build.wasm`

Without that file, the app builds successfully but the game cannot fully start at runtime. The in-app error overlay will tell you this as well.

## How the runtime was extracted

The extracted runtime is stored at:

- `app/src/main/assets/web/unity-framework.js`

If you need to regenerate it from the original text bundle:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\extract-unity-framework.ps1 `
  -InputFile "C:\Users\gowda\OneDrive\Desktop\code.txt" `
  -OutputFile ".\app\src\main\assets\web\unity-framework.js"
```

## Build

From the repo root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

## Play Store readiness checklist

Before publishing, you still need to do the normal store-owner steps:

1. Add the missing `build.wasm` asset from the original web build.
2. Replace the placeholder app icon if you want branded artwork.
3. Set your final `applicationId` if you want a different package name than `com.gowda.crimehunter`.
4. Create your upload keystore and sign the release bundle.
5. Upload the generated `.aab` from `app/build/outputs/bundle/release/`.

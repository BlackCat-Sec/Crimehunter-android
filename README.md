# Crimehunter Android Wrapper

This repository turns the provided web game bundle into an Android Kotlin app that is structured for Google Play submission.

## What is included

- Kotlin Android app using a fullscreen `WebView`
- Local asset hosting through `WebViewAssetLoader`
- Android-safe Poki bridge stubs so the Unity runtime can boot without the original ad SDK
- Release build configuration with shrinking and ProGuard enabled
- A small extraction script that pulls the Unity runtime out of the original `code.txt`
- The generated `build.wasm` asset compiled from the provided `code2.txt`
- A rebuild script that normalizes the original WAT before compiling it into a browser-compatible wasm binary

## Current asset state

The original files you provided were:

- `code.txt`: Unity JavaScript runtime bundle
- `code2.txt`: WebAssembly text format source for the game binary

Those have now been converted into:

- `app/src/main/assets/web/unity-framework.js`
- `app/src/main/assets/web/build.wasm`

## How the runtime was extracted

The extracted runtime is stored at:

- `app/src/main/assets/web/unity-framework.js`

If you need to regenerate it from the original text bundle:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\extract-unity-framework.ps1 `
  -InputFile "C:\Users\gowda\OneDrive\Desktop\code.txt" `
  -OutputFile ".\app\src\main\assets\web\unity-framework.js"
```

The generated WebAssembly binary is stored at:

- `app/src/main/assets/web/build.wasm`

If you ever need to regenerate `build.wasm` from the original `code2.txt`, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\compile-unity-wasm.ps1 `
  -InputFile "C:\Users\gowda\OneDrive\Desktop\code2.txt" `
  -OutputFile ".\app\src\main\assets\web\build.wasm"
```

This step matters because the provided WAT uses compact inline imports and also ends with stray `vv` characters. The rebuild script normalizes those imports and compiles with the WebAssembly features that Android WebView accepts.

## Build

From the repo root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

## Play Store readiness checklist

Before publishing, you still need to do the normal store-owner steps:

1. Test the game on a real Android phone or emulator.
2. Replace the placeholder app icon if you want branded artwork.
3. Set your final `applicationId` if you want a different package name than `com.gowda.crimehunter`.
4. Create your upload keystore and sign the release bundle.
5. Upload the generated `.aab` from `app/build/outputs/bundle/release/`.

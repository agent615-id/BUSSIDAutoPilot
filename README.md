# BUSSID Auto Pilot — starter project

This is an Android starter project configured for Android 15 (API 35), landscape orientation, and cloud APK builds through GitHub Actions.

## Build without Android Studio
1. Create a new **public or private** GitHub repository.
2. Upload the contents of this folder to the repository root (the `.github` folder must be included).
3. Open the repository's **Actions** tab.
4. Select **Build Android APK** and click **Run workflow** (or push to `main`).
5. Open the completed workflow run and download the `BUSSID-AutoPilot-debug` artifact.
6. Extract the ZIP and install `app-debug.apk` on your Android phone. You may need to allow installation from that browser/file manager.

## Current scope
This first starter build only displays a basic dashboard and requests Android screen-capture permission. It does **not** yet capture/process frames or control BUSSID. Those features need to be implemented and tested next. Android may restrict simultaneous screen capture and touch automation; the app must obtain explicit permissions and should be tested safely.

## Requirements
- JDK 17 in GitHub Actions
- Gradle 8.9
- Android Gradle Plugin 8.7.3

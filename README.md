# Nexus Android

This is the Android branch of Nexus. It is isolated from the PC branch.

## Implemented
- Native Android APK project.
- Unified Chat + Research conversation.
- Gemini REST API with a user-supplied key; no key is hard-coded.
- Optional Google Search grounding in Research mode.
- Microphone speech input with runtime permission handling.
- Text-to-speech replies.
- Landscape-first dark-blue interface.
- Persistent model/API-key settings.
- GitHub Actions debug APK build.

## Not enabled yet
- Hey Nexus wake-word detection.
- Always-on/background listening.
- Autonomous device control.

## Build
Open the android branch in Android Studio, sync Gradle, and run the app configuration.

The GitHub Actions workflow builds app-debug.apk for pushes to this branch.

## API key
Enter your own Gemini API key in Nexus Settings. It is stored in the app's private preferences and is never committed to this repository.

## Branches
- main: original blueprint.
- pc-main: PC branch.
- android: Android implementation.

# ErdStream Project

## Platform
Android music streaming client for e-ink/minimal devices, especially Mudita Kompakt.

## Current stack
- Kotlin / Jetpack Compose
- Mudita Mindful Design (`com.mudita:MMD:1.0.0`)
- minSdk 28, target/compile SDK 35
- Retrofit + Moshi + OkHttp for Subsonic/Navidrome APIs
- Media3 ExoPlayer/session/database + OkHttp data source
- EncryptedSharedPreferences for server credentials
- Android lifecycle-process/runtime-compose
- Gradle Kotlin DSL

## Product constraints
- Grayscale, low-animation, e-ink-friendly UI is intentional.
- Background playback, media session/notification controls, and cache reliability are core behavior.
- Server URL/credentials are sensitive; never log or expose them.
- Support Subsonic-compatible servers and server-side transcoding.

## Standard verification
- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- applicable connected/instrumentation tests
- device/emulator playback checks for media changes

## Release/signing
Release signing depends on local `keystore.properties`; never commit signing material.

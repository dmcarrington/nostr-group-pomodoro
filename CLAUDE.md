# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android Pomodoro timer app with dark cyberpunk theme and Nostr protocol integration. Supports Nostr login via Amber (NIP-55), creating a new nsec, or importing an existing nsec. Timer runs as a foreground service with configurable presets.

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew test                  # Run all unit tests
./gradlew test --tests "com.pomodoro.nostr.TestClassName"  # Run a single test
./gradlew lint                  # Lint check
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Technology Stack

- **Language**: Kotlin, **UI**: Jetpack Compose, **DI**: Hilt
- **Nostr**: rust-nostr Kotlin bindings (`org.rust-nostr:nostr-sdk:0.35.0`) + JNA
- **Auth**: Amber NIP-55 (primary), local nsec (fallback)
- **Relays**: OkHttp WebSocket (not rust-nostr's built-in relay client)
- **Min SDK**: 26, **Target SDK**: 35, **JDK**: 17

## Architecture

MVVM with Hilt DI. Package: `com.pomodoro.nostr`

- `nostr/` — `KeyManager` (auth + Amber NIP-55 + EncryptedSharedPreferences), `NostrClient` (relay WebSocket manager)
- `timer/` — `TimerService` (foreground service), `TimerModels` (state/presets), `TimerPreferences`
- `viewmodel/` — `AuthViewModel`, `TimerViewModel`, `SettingsViewModel`
- `ui/screens/` — `auth/AuthScreen`, `timer/TimerScreen`, `settings/SettingsScreen`
- `ui/navigation/NavGraph.kt` — Routes: auth → timer → settings
- `ui/theme/` — Cyberpunk dark theme (neon cyan/magenta/purple on black)
- `di/AppModule.kt` — Hilt singleton providers

## Key Patterns

- **Dual signing**: Every signing operation checks `keyManager.isAmberConnected()` and branches to Amber intent flow or local `Keys` signing
- **Timer state**: Shared via `TimerService.timerState` companion `StateFlow` — observed by `TimerViewModel`
- **Amber callback**: Deep link scheme `nostr-pomodoro://callback`, handled in `MainActivity.onNewIntent()`
- **Nostr layer**: Ported from reference app in `nostr-ig-app/` — battle-tested code

## References

- `thoughts/init.md` — Original requirements
- `thoughts/shared/plans/2026-02-16-pomodoro-app.md` — Implementation plan
- `nostr-ig-app/` — Reference Nostr app (source of ported auth layer)

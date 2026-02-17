# Time Tribe

A cyberpunk-themed Pomodoro timer for Android with Nostr protocol integration. Focus with friends, track sessions on relays, and compete on leaderboards.

## Features

### Pomodoro Timer
- Animated circular timer with neon glow effects and phase-based color coding
- Three built-in presets: Classic (25/5/15), Long Focus (50/10/30), Short Sprint (15/3/10)
- Fully customizable work, break, and long break durations
- Runs as a foreground service so the timer continues when the app is backgrounded
- Visual session dots track progress toward long breaks
- Automatic phase progression: work, short break, work, ..., long break

### Nostr Integration
- **Authentication**: Sign in with [Amber](https://github.com/greenart7c3/Amber) (NIP-55), import an existing nsec, or generate a new keypair
- **Session publishing**: Completed pomodoro sessions are automatically published as kind 8808 events to connected relays
- **Profile management**: Edit your Nostr profile (display name, about, NIP-05, Lightning address, etc.) and publish to relays
- **Relay management**: Add, remove, and monitor relay connections with live status indicators

### Social Features
- **Friends list**: Add contacts by npub, search, or QR code scan
- **Leaderboards**: Rank against friends by session count over daily, weekly, and monthly timeframes
- **Activity matrix**: GitHub-style contribution grid showing your focus history over the past 26 weeks

### Profile Pictures
- Upload profile pictures from your device via Blossom protocol (BUD-01)
- EXIF metadata is automatically stripped for privacy
- Failover across multiple Blossom servers (primal.net, nostr.download, files.v0l.io)
- Profile picture displays on the timer screen

### QR Codes
- Display your npub as a QR code for easy sharing
- Scan QR codes to add friends (CameraX + ML Kit barcode scanning)

## Building

### Requirements
- JDK 17
- Android SDK with API level 35
- Android device or emulator running API 26+

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore.properties)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.pomodoro.nostr.TestClassName"

# Lint check
./gradlew lint
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

### Release Signing

To build a signed release APK, create a `keystore.properties` file in the project root:

```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **DI**: Hilt
- **Nostr**: rust-nostr Kotlin bindings (`org.rust-nostr:nostr-sdk:0.35.0`)
- **Relays**: OkHttp WebSocket
- **Images**: Coil (display), Blossom protocol (upload)
- **QR**: ZXing (generation), CameraX + ML Kit (scanning)
- **Storage**: EncryptedSharedPreferences for key material

## Architecture

MVVM with Hilt dependency injection. The timer runs as an Android foreground service with state exposed via a companion `StateFlow`. Every Nostr signing operation supports dual paths: Amber external signer (NIP-55) or local key signing.

## License

MIT

# Google Error Prone annotations (used by Tink crypto, not needed at runtime)
-dontwarn com.google.errorprone.annotations.**

# Java AWT classes (not available on Android, referenced by JNA)
-dontwarn java.awt.**

# rust-nostr JNA rules
-keep class rust.nostr.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep ViewModels
-keep class com.pomodoro.nostr.viewmodel.** { *; }

# Keep Nostr models
-keep class com.pomodoro.nostr.nostr.models.** { *; }

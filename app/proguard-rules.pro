# Default ProGuard rules
-keep class com.rajatxo.lutea.** { *; }

# ── Lottie ────────────────────────────────────────────────────────────────────
# Lottie uses reflection to deserialize the JSON animation files into model classes.
# Keep all Lottie model classes and their fields.
-keep class com.airbnb.lottie.** { *; }

# ── Kyant backdrop ──────────────────────────────────────────────────────────
# Kyant uses reflection + Compose graphics layer APIs. Keep everything.
-keep class com.kyant.backdrop.** { *; }

# ── Missing classes (transitive dependencies that R8 can't find) ────────────
# okio (pulled in by Lottie) references javax.annotation.Nullable which isn't in
# the Android runtime. Tell R8 to ignore these missing classes instead of failing.
-dontwarn javax.annotation.**

# ── FFmpegKit ────────────────────────────────────────────────────────────────
# FFmpegKit (community-maintained fork) references com.arthenica.smartexception.java
# at runtime, but the audio-only AAR variant doesn't bundle those classes. Two
# problems need solving:
#
# 1. R8 strips FFmpegKit classes (NoClassDefFoundError at runtime) — keep ALL
#    classes in com.arthenica.ffmpegkit and com.arthenica.smartexception.
# 2. R8 errors out on the missing smartexception references — suppress with -dontwarn.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-keep,allowobfuscation class com.arthenica.ffmpegkit.FFmpegKit { *; }
-keep,allowobfuscation class com.arthenica.ffmpegkit.FFmpegKitConfig { *; }
-keep,allowobfuscation class com.arthenica.ffmpegkit.FFmpegSession { *; }
-keep,allowobfuscation class com.arthenica.ffmpegkit.ReturnCode { *; }
-keep,allowobfuscation class com.arthenica.ffmpegkit.Statistics { *; }
-keep,allowobfuscation class com.arthenica.ffmpegkit.SessionState { *; }
-keepclassmembers class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.smartexception.**
-dontwarn com.arthenica.ffmpegkit.**

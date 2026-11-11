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

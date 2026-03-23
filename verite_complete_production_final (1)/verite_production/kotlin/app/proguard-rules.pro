# Vérité TMR — ProGuard Rules

# ── Retrofit + OkHttp ─────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── Gson ──────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# ── Keep all Vérité data models (used by Gson) ────────────────────────────────
-keep class com.verite.tmr.data.models.** { *; }

# ── Hilt ──────────────────────────────────────────────────────────────────────
-dontwarn com.google.dagger.**
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ── OkHttp WebSocket ──────────────────────────────────────────────────────────
-keep class okhttp3.internal.ws.** { *; }
-dontwarn okhttp3.internal.platform.**

# ══════════════════════════════════════════════════════════════════
# Vérité — ProGuard / R8 Rules for Release Build
# ══════════════════════════════════════════════════════════════════

# ── Keep line numbers for Crashlytics ─────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Retrofit ──────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── Gson ──────────────────────────────────────────────────────────
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Room Database ─────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── TensorFlow Lite ───────────────────────────────────────────────
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ── Firebase ──────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Coil (Image loading) ─────────────────────────────────────────
-dontwarn coil.**

# ── MPAndroidChart ────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ── Apache POI (DOCX parsing) ────────────────────────────────────
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.**

# ── PDFBox ────────────────────────────────────────────────────────
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# ── SceneView (3D) ───────────────────────────────────────────────
-dontwarn io.github.sceneview.**
-keep class io.github.sceneview.** { *; }

# ── App data models (Room entities, API models) ──────────────────
-keep class com.example.myapplication.data.model.** { *; }
-keep class com.example.myapplication.data.network.** { *; }

# ── Kotlin Coroutines ────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Compose ──────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Prevent stripping of BuildConfig ─────────────────────────────
-keep class com.example.myapplication.BuildConfig { *; }

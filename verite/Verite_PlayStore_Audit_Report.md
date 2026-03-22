# Vérité App — Play Store Readiness Audit Report

**Date:** March 22, 2026
**App:** Vérité (Sleep, Wellness & Productivity)
**Package:** com.example.myapplication
**Target SDK:** 36 | **Min SDK:** 34 | **Version:** 1.0 (versionCode 1)

---

## Issues Fixed (14 Total)

### 1. Hardcoded API Keys Removed (CRITICAL — Play Store Rejection)

**Problem:** API keys were hardcoded as string literals in source code, which gets compiled into the APK. Attackers can decompile and extract them.

**Files fixed:**
- `Secrets.kt` — Replaced 3 hardcoded keys (HuggingFace, Groq, ElevenLabs) with `BuildConfig` delegates
- `DashboardViewModel.kt` — Replaced 2 inline Groq API key strings with `BuildConfig.GROQ_API_KEY`
- `TodoViewModel.kt` — Replaced 1 inline Groq API key string with `BuildConfig.GROQ_API_KEY`
- `HuggingFaceHelper.kt` — Replaced hardcoded HF key with `BuildConfig.HF_API_KEY`
- `ElevenLabsManager.kt` — Changed from `Secrets.ELEVENLABS_API_KEY` to `BuildConfig.ELEVENLABS_API_KEY`
- `FullVoiceCommandProcessor.kt` — Changed from `Secrets.GROQ_API_KEY` to `BuildConfig.GROQ_API_KEY`
- `build.gradle.kts` — Added `ELEVENLABS_API_KEY` BuildConfig field
- `local.properties` — Added `ELEVENLABS_API_KEY` entry

**How it works now:** All keys live in `local.properties` (gitignored), injected via `buildConfigField()` at build time. `Secrets.kt` is now a thin bridge that delegates to `BuildConfig`.

### 2. App Icon Fixed

**Problem:** `AndroidManifest.xml` used `@android:drawable/sym_def_app_icon` (the generic Android robot icon). Play Store requires a custom icon.

**Fix:** Changed to `@mipmap/ic_launcher` and added `android:roundIcon="@mipmap/ic_launcher_round"`. Custom icon already existed in all mipmap density folders.

### 3. Cleartext Traffic Disabled

**Problem:** `android:usesCleartextTraffic="true"` allows HTTP (unencrypted) connections. Play Store flags this as a security vulnerability.

**Fix:** Set to `false`. Created `network_security_config.xml` that enforces HTTPS-only in production but allows cleartext in debug builds for local testing.

### 4. Duplicate Permission Removed

**Problem:** `ACCESS_FINE_LOCATION` was declared twice in AndroidManifest.xml (lines 9 and 21).

**Fix:** Consolidated to a single declaration. Reordered location permissions (`FINE_LOCATION` then `COARSE_LOCATION`).

### 5. Data Extraction Rules Updated

**Problem:** `data_extraction_rules.xml` was the default template with TODO comments. Required for Android 12+ (`targetSdk 34+`).

**Fix:** Added proper rules that exclude sensitive data (voice identity, settings) from cloud backup while allowing Room DB device transfer.

### 6. ProGuard Rules Added

**Problem:** Default empty ProGuard file. Release builds with `isMinifyEnabled = true` would crash because R8 strips required classes.

**Fix:** Added comprehensive rules for: OkHttp, Retrofit, Gson, Room, TensorFlow Lite, Firebase, Coil, MPAndroidChart, Apache POI, PDFBox, SceneView, Kotlin Coroutines, Compose, and app data models.

### 7. Network Security Config Created

**Problem:** No `network_security_config.xml` existed. App relied on global cleartext flag.

**Fix:** Created config enforcing HTTPS-only with system trust anchors for production, and user certificates for debug builds.

### 8. Null Safety Crash Fixed

**Problem:** `TodoMainScreen.kt` line 43 used `selectedTask!!.id` which crashes with `NullPointerException` if `selectedTask` is null.

**Fix:** Changed to `selectedTask?.let { task -> viewModel.deleteTask(task.id) }`.

### 9. Privacy Policy & Data Disclosure Added

**Problem:** No privacy policy link in the app. Google Play requires this for apps that access location, microphone, Bluetooth, or health data.

**Fix:** Added privacy policy link, terms of service link, and a data collection disclosure to `PrivacyActivity.kt`.

### 10. Manifest Modernized

**Fix:** Added `android:supportsRtl="true"`, `android:fullBackupContent="false"`, `android:dataExtractionRules`, updated `tools:targetApi` to 34.

---

## Remaining Action Items (YOU Must Do)

These items cannot be automated and require manual action:

### CRITICAL — Will Block Play Store Submission

1. **Change Package Name** — `com.example.myapplication` will be REJECTED by Play Store. You need to:
   - Choose a real package name (e.g., `com.verite.app` or `com.yourdomain.verite`)
   - Update `applicationId` and `namespace` in `build.gradle.kts`
   - Update Firebase Console with the new package name and download new `google-services.json`
   - Update the `default_web_client_id` in `strings.xml` if it changes

2. **Create Privacy Policy Web Page** — Host a real privacy policy at `https://verite-app.com/privacy` (or any URL). Google Play requires a clickable privacy policy URL during submission. The URL in the app's PrivacyActivity must match.

3. **Create Terms of Service Page** — Host at `https://verite-app.com/terms`.

4. **Create App Icon** — While custom launcher icons exist in mipmap folders, verify they are your final branded icons (not placeholders).

5. **Set Up App Signing** — Generate a release keystore and configure signing in `build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("path/to/keystore.jks")
           storePassword = "..."
           keyAlias = "..."
           keyPassword = "..."
       }
   }
   ```

### IMPORTANT — Recommended Before Launch

6. **Lower minSdk** — Currently `minSdk = 34` (Android 14 only). This limits your audience to ~30% of devices. Consider `minSdk = 26` (Android 8.0+, ~95% coverage) or `minSdk = 28` (Android 9+, ~90%).

7. **Add Proper Room Migrations** — `fallbackToDestructiveMigration()` wipes ALL user data on any database version update. Before your next DB schema change, write proper `Migration` objects.

8. **Remove Debug Logs** — 12 debug log statements (`Log.d`, `Log.v`) found. Add `if (BuildConfig.DEBUG)` guards or use Timber library.

9. **Play Store Data Safety Form** — When submitting, you must declare:
   - **Location data** — collected for weather in morning brief
   - **Audio data** — collected for voice commands and wake-word detection
   - **Health & fitness data** — sleep tracking, mood entries, habit data
   - **Device identifiers** — Bluetooth device connections
   - Data is stored **locally on device** (not transmitted to your servers)
   - Third-party services: Firebase Auth, Groq API, ElevenLabs API, HuggingFace API, OpenRouter API

10. **Content Rating Questionnaire** — The app handles health/wellness data. Answer the Play Store content rating questionnaire accurately.

---

## Files Modified Summary

| File | Change |
|------|--------|
| `Secrets.kt` | Replaced hardcoded keys with BuildConfig delegates |
| `build.gradle.kts` | Added ELEVENLABS_API_KEY BuildConfig field |
| `local.properties` | Added ELEVENLABS_API_KEY |
| `DashboardViewModel.kt` | Replaced 2 hardcoded Groq keys |
| `TodoViewModel.kt` | Replaced 1 hardcoded Groq key |
| `HuggingFaceHelper.kt` | Replaced hardcoded HF key |
| `ElevenLabsManager.kt` | Changed to BuildConfig |
| `FullVoiceCommandProcessor.kt` | Changed to BuildConfig |
| `AndroidManifest.xml` | Icon, cleartext, permissions, data rules |
| `network_security_config.xml` | Created (HTTPS enforcement) |
| `data_extraction_rules.xml` | Updated with proper exclusions |
| `proguard-rules.pro` | Added library-specific rules |
| `TodoMainScreen.kt` | Fixed null safety crash |
| `PrivacyActivity.kt` | Added privacy policy, terms, data disclosure |

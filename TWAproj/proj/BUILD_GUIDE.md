# 360 Android App — Build & Publish Guide

## What's in this package

```
360-android-release/
├── 360-android/          ← The redesigned mobile web app
│   ├── index.html        ← Full Android-optimized SPA
│   ├── assets/css/android.css
│   └── manifest.json
│
├── 360-twa-project/      ← Ready-to-compile Android TWA project
│   ├── app/
│   │   ├── build.gradle
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── res/
│   └── build.gradle
│
└── assetlinks.json       ← Host this at your domain
```

---

## Step 1 — Deploy the redesigned site

Push the contents of `360-android/` to your `360-search.com` GitHub Pages repo.
This replaces the old site with the new mobile-first Android design.

```bash
cp -r 360-android/* /path/to/your/360-repo/
git add . && git commit -m "Android mobile redesign v2.0.3"
git push
```

---

## Step 2 — Generate your signing keystore (one-time)

```bash
keytool -genkey -v \
  -keystore 360-release.jks \
  -alias 360key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=360 Inc, OU=Mobile, O=360 Digital, L=NYC, ST=NY, C=US"
```

Keep `360-release.jks` safe — you need it for every update.

---

## Step 3 — Get your SHA-256 fingerprint

```bash
keytool -list -v \
  -keystore 360-release.jks \
  -alias 360key \
  -storepass YOUR_STORE_PASSWORD \
  | grep "SHA256"
```

Copy the fingerprint (looks like `AB:CD:12:34:...`).

---

## Step 4 — Update assetlinks.json

Replace `REPLACE_WITH_YOUR_SHA256_FINGERPRINT_AFTER_SIGNING` in `assetlinks.json`
with the fingerprint from Step 3.

Then add the file to your site repo:

```bash
mkdir -p /path/to/your/360-repo/.well-known/
cp assetlinks.json /path/to/your/360-repo/.well-known/assetlinks.json
git add . && git commit -m "Add assetlinks for TWA"
git push
```

Verify it's live: https://360-search.com/.well-known/assetlinks.json

---

## Step 5 — Update app/build.gradle with your keystore

Open `360-twa-project/app/build.gradle` and fill in the `signingConfigs` block:

```groovy
signingConfigs {
    release {
        storeFile file('/path/to/360-release.jks')
        storePassword 'YOUR_STORE_PASSWORD'
        keyAlias '360key'
        keyPassword 'YOUR_KEY_PASSWORD'
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release   // add this line
        minifyEnabled true
        ...
    }
}
```

---

## Step 6 — Build the APK / AAB

**Requirements:** Android Studio installed, or Android SDK with Gradle.

### Option A — Android Studio (easiest)
1. Open Android Studio
2. File → Open → select the `360-twa-project` folder
3. Let it sync and download dependencies (~2 min)
4. Build → Generate Signed Bundle/APK
5. Choose APK, select your keystore, build release

### Option B — Command line (fastest)

```bash
cd 360-twa-project

# Build debug APK (no signing needed, for testing)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK (for Galaxy Store)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Build AAB (preferred for Galaxy Store)
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

---

## Step 7 — Install on your Galaxy device (testing)

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or share the APK file directly via USB / email.

---

## Step 8 — Publish to Galaxy Store

1. Go to https://seller.samsungapps.com
2. Create account (free) → Add New App → Android
3. Upload your `.aab` file
4. Fill in:
   - **App name:** 360
   - **Category:** Tools or Search
   - **Screenshots:** 4 minimum (1080×1920)
   - **Short description:** Search, AI, Weather, News and Games — all in one.
   - **Full description:** (see below)
   - **Privacy Policy URL:** https://360-search.com/privacypolicy.html
5. Age rating: complete the questionnaire (General)
6. Submit for review (~3–5 days)

### Store description template

```
360 is your all-in-one digital workspace built for Android.

Search the web with blazing speed, chat with AI, check live weather, 
read the news, track stocks, translate languages, and play games — 
all without leaving the app.

FEATURES
• 🔍 Fast web search with image results
• 🤖 Built-in AI assistant
• 🌤 Live weather from your location
• 📰 News feed
• 🎮 10+ built-in games
• 📈 Stock tracker
• 🌐 Language translator
• 🎨 6 color themes + dark mode
• 🔒 Secure sign-in with Google or GitHub

Lightweight, fast, and beautiful on every Samsung device.
```

---

## Troubleshooting

**TWA shows browser UI instead of standalone:**
→ Make sure `assetlinks.json` is live and the SHA-256 fingerprint matches exactly.

**App crashes on launch:**
→ Verify `360-search.com` is reachable and serving HTTPS.

**Galaxy Store rejects the APK:**
→ Use `.aab` (Android App Bundle) instead of `.apk`.
→ Make sure `targetSdk` is 34 or higher.
→ Ensure privacy policy URL is live.

---

## File to host at your domain

```
https://360-search.com/.well-known/assetlinks.json
```
This is **required** for the TWA to work without browser chrome showing.

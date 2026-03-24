# stims

An Android app that keeps your screen awake while specific apps are in the foreground.

## What it does

Stims lets you select apps that should prevent your screen from turning off. When a "stimmed" app is active in the foreground, the screen stays on at full brightness. When you switch to a different app, the screen resumes normal sleep behavior.

---

## Permissions

Stims requires **Usage Access** permission to detect which app is in the foreground so it can keep the phone awake.

On first launch the app will redirect you to the system settings screen. To enable it:

1. Go to **Settings → Apps → Special app access → Usage access**
2. Find **Stims** in the list and toggle it on
3. Return to the app

Without this permission the background service cannot detect foreground apps and the screen will not be kept awake.

---

# Building from source

**Requirements**

- Android Studio or the Android SDK command-line tools
- JDK 8+

**Steps**

```bash
git clone https://github.com/acidburnmonkey/stims.git
cd stims
./gradlew assembleDebug
```

The APK will be output to:

```
app/build/outputs/apk/debug/stims-<version>.apk
```

For a release build:

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/stims-<version>.apk
```

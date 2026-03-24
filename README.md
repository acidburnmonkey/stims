# stims

An Android app that keeps your screen awake while specific apps are in the foreground.

## What it does

Stims lets you select apps that should prevent your screen from turning off. When a "stimmed" app is active in the foreground, the screen stays on at full brightness. When you switch to a different app, the screen resumes normal sleep behavior.

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

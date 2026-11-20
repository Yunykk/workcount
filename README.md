# WorkCount

Clock out with the hours already counted.

WorkCount is an Android shift timer and journal for paid time and estimated earnings. Set an hourly rate, record a shift, then check what you worked before payday.

![Illustrated WorkCount flow from shift timer to journal to local export](docs/readme/shift-flow.svg)

*Illustrated workflow. The Android app has Home, Journal, and Settings tabs.*

## Try a shift

1. Set a default hourly rate on first launch. You can also save workplaces with their own rates.
2. Tap **Start** on **Home**. Pause for a break, resume when you're back, then tap **Stop**.
3. Open **Journal** to review or edit the shift. Filter by date, place, or status.
4. Use the export tools to save a filtered journal as CSV or PDF. Make a JSON backup if you want to restore the data later.

The app also has a home-screen widget, timer reminders, optional auto-stop, and a biometric or PIN lock.

## Install a debug build

Use Android Studio, Android SDK 36, and JDK 21 for the Gradle daemon. The Android modules target Java 17. The app runs on Android 8.0 or later.

```bash
git clone https://github.com/Yunykk/workcount.git
cd workcount
bash ./gradlew assembleDebug
```

On Windows, run `.\gradlew.bat assembleDebug` from the checkout. You can also open the checkout in Android Studio and run the `app` configuration on a device or emulator.

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected device with USB debugging enabled and Android platform tools on your PATH, run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Run tests with `bash ./gradlew test` or `.\gradlew.bat test` on Windows.

## Data and limits

WorkCount stores shifts on the device. There is no account or automatic sync. Optional location access can fill the place on an active shift. Earnings are estimates from recorded hours and rates. Keep a JSON backup before clearing app data or changing phones.

Built with Kotlin, Jetpack Compose, Room, and DataStore. [Contributing](CONTRIBUTING.md) · [MIT license](LICENSE)

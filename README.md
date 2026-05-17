# WorkCount

WorkCount is a small Android app for tracking work shifts, paid hours, and simple earnings.

It is built for people who need a lightweight timer and a basic shift journal without accounts, subscriptions, or server sync.

## Features

- Start, pause, resume, and stop work shifts.
- Track paid time with optional break time.
- Store work places with default hourly rates.
- Filter shifts by day, week, month, custom range, place, and status.
- Export the current journal view to CSV or PDF.
- Create and restore local JSON backups.
- Keep a home-screen widget for the active shift.
- Protect the app with device biometrics or PIN.
- Get forgotten-timer reminders or auto-stop long-running shifts.

## Tech Stack

- Kotlin
- Android Jetpack Compose
- Material 3
- Room
- DataStore
- Coroutines
- Gradle version catalog

## Project Structure

```text
app/           Android app, UI, services, widget, location integration
core/domain/   Domain models, repositories, payroll periods, calculations
core/data/     Room, DataStore, CSV/PDF export, backup implementation
```

## Requirements

- Android Studio with JDK 17
- Android SDK 36
- Gradle wrapper included in this repository

## Build

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Test

```bash
./gradlew test
```

## Privacy

WorkCount stores data locally on the device. The app does not include analytics, ads, accounts, or a remote backend.

Location access is optional and is only used to fill the place field for an active shift.

## License

MIT. See [LICENSE](LICENSE).

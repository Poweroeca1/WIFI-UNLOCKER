# WiFi Unlocker (Android)

An Android 16-compatible Wi-Fi network scanner built with Kotlin. This first version lists nearby networks visible to the phone, including signal strength, frequency, and advertised security capabilities.

## Features
- Scan nearby Wi-Fi networks using Android's Wi-Fi APIs.
- Display SSID, signal strength, frequency, and security capabilities.
- Dark interface with yellow accents.
- Requests Android runtime permissions and explains when location services are needed.

## Important limitations
- This app does **not** crack Wi-Fi passwords or bypass network security.
- Android and device firmware limit how often Wi-Fi scans can run.
- Scan results require the appropriate permissions; Android may require Location services to be enabled.
- Joining a protected network still requires valid credentials or an authorized connection method.

## Build APK on GitHub
The workflow in `.github/workflows/android.yml` builds a debug APK automatically when code is pushed. Open the repository's **Actions** tab, select the latest successful workflow run, and download the `app-debug` artifact.

## Tech
- Kotlin
- Android Gradle Plugin
- compileSdk / targetSdk 36 (Android 16)

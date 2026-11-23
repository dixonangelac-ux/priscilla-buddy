# Priscilla's Buddy — Android

The Fire OS / Android application is built from local assets in `app/src/main/assets/`. It does not load the publicly hosted GitHub Pages demo. The GitHub Pages site remains an independent demo.

## Build
The workflow at [Build Buddy Android APK](../../.github/workflows/build-buddy-apk.yml) generates a debug APK for development tests. The artifact is named `priscilla-buddy-debug-apk`.

## Installation for testing
Download and extract the successful build's artifact ZIP; from your existing Windows ADB directory run:
```powershell
.\adb.exe install -r "C:\platform-tools\app-debug.apk"
```
Do not uninstall an older Android Buddy build with real data unless its data is backed up; Android app removal deletes app-private information and its encryption key.

## First launch
An adult must set a 6–12 digit PIN. Buddy opens in child mode by default. The child can add and edit one-time and weekly activities and skip individual repeating occurrences. Parent access is available from the bottom of the app; after entering the PIN the parent can edit routines and protected events, change PIN and settings, or exit to Android Home.

## Security and limits
See [SECURITY_AND_SYNC.md](SECURITY_AND_SYNC.md) for the detailed architecture and outstanding release gates.

- Offline local Android build with encrypted local data and no Internet permission.
- PIN is a local convenience/access control; it is **not** a replacement for Android's lock-screen credential, parental restrictions, or server account authentication.
- Fullscreen immersive mode is **not** lock task mode or tamper-proof kiosk confinement. Android system UI can still be invoked.
- Phone and tablet currently keep independent local data; **no sync is implemented yet**.
- The parent PIN is provisioned per device. A true parent profile synchronized between devices needs a private backend, authentication and secure device enrollment.
- Google Calendar integration has been designed but not yet connected.
- Debug-signed APK is for testing. Production requires protected release signing, credentialed backend, permission tests, device management and a security review.

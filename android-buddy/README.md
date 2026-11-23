# Priscilla's Buddy — Fire OS wrapper
A small Android WebView wrapper for the existing GitHub Pages application.

## Build
Open the GitHub Actions tab, select **Build Buddy Android APK**, then choose **Run workflow**. Download the artifact named `priscilla-buddy-debug-apk` after the build succeeds. An Android Studio local build is also possible (Android Gradle Plugin 8.6.1; Gradle 8.7; JDK 17, Android SDK 35).

## Install (Windows)
Extract the downloaded artifact ZIP, then run:
```powershell
.\adb.exe install -r "C:\path\to\app-debug.apk"
```
If already installed from another signing key, reinstall only after exporting any Buddy data.

## Security and limits
- Wrapper requests only Internet access.
- Only HTTPS resources under `https://dixonangelac-ux.github.io/priscilla-buddy/` are accepted.
- SSL errors are rejected, mixed content is forbidden, WebView debugging is disabled.
- JavaScript and DOM storage are enabled because Buddy requires both.
- This is fullscreen, **not tamper-proof kiosk mode**; Android system controls can still exit.
- Buddy's current PIN is convenience-only; the web app's code is public and not a secure authentication boundary.
- WebView storage is separate from Silk storage. Export from Silk and import in Buddy, or start fresh.
- GitHub Actions produces a **debug-signed APK**, suitable for personal testing but not production distribution. Reproducible release builds should use a protected signing key and review/update dependencies.
- GitHub Pages content may change without an APK update. Review app changes before deploying them, keep the GitHub account protected with MFA, and don't commit a child's private schedule.
- Site requires connectivity on first load; offline behavior depends on WebView service-worker caching and should be tested, not assumed.

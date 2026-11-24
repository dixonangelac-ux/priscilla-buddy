# Buddy security, profiles, calendar and synchronization contract

## Shipped local Android behavior
- A single APK can be installed on the Fire HD 8 and a parent's Android phone.
- The APK contains its own UI and **does not access GitHub Pages or any public website at runtime**.
- Android manifest requests **no Internet permission** in this offline build.
- Task and event data are encrypted at rest with Android Keystore AES-256-GCM and saved in app-private preferences.
- WebView serves bundled files through AndroidX WebViewAssetLoader at a reserved HTTPS origin, blocks any navigation or resource access outside /assets/, disables WebView debugging, and disables third-party networking.
- Parent PIN is locally created at first launch, hashed with PBKDF2-HMAC-SHA256 with per-install random salt and 160,000 iterations. A 5-attempt lockout lasts five minutes.
- Child mode can create/edit/delete only child-owned activities through the UI; parent-owned events are read-only. The owner field is retained in the activity model.
- Weekly recurring events support start date, end date and per-occurrence skips. One-off events are first-class.
- App restarts default to the child interface, not a remembered elevated parent session.
- **No credentials, sensitive family data or real schedules are embedded in source.**

## Security limitations
- App-private encryption protects data at rest; **a logged-in or compromised OS and unlocked app can still access displayed data.**
- The local PIN is not a cloud identity and child edit restrictions within a local UI are **not server-enforced authorization**.
- Fullscreen/immersive mode is **not true kiosk mode**. Fire OS screen pinning or supported device-owner provisioning must be tested separately. Don't disable the Fire launcher.
- The parent can exit using the app's Parent > Security view, and Android device-level controls remain available.
- The debug APK produced by Actions is for testing; production requires a protected signing key, signed release, and verified artifact provenance.
- Existing Silk browser data do **not** automatically migrate into this app.
- No phone-to-tablet synchronization currently occurs; installations are independent. Do not claim data sync or remote revocation works until backend and authentication are deployed and tested.
- Parent PINs are **per installation** until authenticated account enrollment exists.
- JSON import/export should be encrypted/password protected if added; do not publish plain backups of a minor's schedule.

## Target architecture for private sync (Firebase Spark: Authentication + Cloud Firestore)
1. Parent signs in using a provider with passkeys or MFA. Parent role is derived from authenticated server identity, never from a client-selected profile field.
2. Parent creates a private family/workspace. The device enrolls via a short-lived parent-authorized pairing challenge; backend issues revocable scoped device credential. No shared or hardcoded PIN is used as a remote credential.
3. Server validates every read/write against workspace membership, device role, record ownership and a server-side permissions table. Child may create and edit child-owned activities and may propose edits to locked events; adult can edit anything in the workspace.
4. Use Firestore's built-in Android offline persistence for pending writes; implement explicit conflict and recurrence-exception handling, with stable event IDs and idempotent mutations. Avoid introducing a second competing local sync engine.
5. Use Firestore Security Rules to deny all access by default and enforce family membership, event ownership, and role scopes. Never trust a client-selected profile as an authorization role. Only calendar-appropriate events (not chores or rewards) can be explicitly selected for Google Calendar two-way synchronization. OAuth credentials stay on a confidential backend, never inside the APK. Use the minimum calendar scope, refresh tokens encrypted at rest and a single selected calendar. Preserve Google event IDs, recurring-instance IDs and sync tokens.
6. A newly invited adult (for example another parent) gets a **distinct account and explicitly configured scoped permissions**, not access to the existing parent's identity or Google OAuth token.
7. A lost child device can be revoked server-side. Local offline content still requires Android device lock and physical security; remote revocation cannot erase offline content until device reconnects.
8. All sensitive endpoints should be rate-limited, audited and covered by permission tests. No public family dashboard, no unauthenticated reads, no device identity inferred from easily forged client claims.

## Calendar record model (v2 local)
```json
{
  "id": "UUID or stable random identifier",
  "title": "Birthday party",
  "icon": "🎉",
  "type": "once | weekly",
  "date": "YYYY-MM-DD for once",
  "startDate": "YYYY-MM-DD for weekly",
  "weekday": 6,
  "until": "YYYY-MM-DD optional",
  "time": "HH:mm local optional",
  "owner": "child | parent",
  "visibility": "child",
  "exceptions": ["YYYY-MM-DD"],
  "updatedAt": "ISO UTC timestamp",
  "deleted": false
}
```
Future fields: IANA time zone, duration, arbitrary RRULE, recurrence overrides, external calendar ID, revision and modified_by (server validated). A time zone must be added before automatic cross-device sync to avoid DST mistakes.

## Next security release gates
- Build APK from pinned/maintained dependencies; test it on Fire OS 8 and Pixel Android.
- Test PIN setup, lockout, restart, editing protected events, offline persistence, and repair/reinstall behavior.
- Add secure parent-account authentication and paired device enrollment on a live confidential backend.
- Test RLS/backend role enforcement against malicious and unauthenticated API requests.
- Implement and test private sync including offline merge conflicts and device revocation.
- Perform Android kiosk exit/parent-control tests on actual Fire OS firmware.
- Add optional Google Calendar adapter only after backend and event model are fully validated.


## Selected backend — November 2026
Firebase **Spark** is the agreed backend. See [Firebase setup and security rules](../firebase/README.md). A draft Supabase schema remains only as historical design work and is not active or deployed. No Firebase project or connected synchronization has yet been configured; do not use the offline-only APK as proof that device enrollment or private sync is operational.

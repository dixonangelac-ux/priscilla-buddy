# Firebase Spark setup for Priscilla's Buddy

**Status:** Firebase is selected as the future sync service. This repository does **not** contain a Firebase project or a working authenticated synchronization connection yet. The Android app remains offline-only until enrollment and access-control tests are implemented.

## Cost constraints
- Choose **Spark (no-cost)**. Do not enter a credit card or link a billing account.
- Use Cloud Firestore and Firebase Authentication, not Cloud Functions, Cloud Run, Cloud Messaging, or phone/SMS verification.
- Firebase's Spark quota is ample for a family-sized calendar; a quota overage can make the affected service unavailable until quota resets instead of billing you.
- Google Calendar integration remains **off** until authenticated OAuth, access controls and secure token handling are built.

## Project selected
- Firebase project ID: `priscilla-buddy`
- Android package name: `com.priscillabuddy.app`
- Firebase console: https://console.firebase.google.com/project/priscilla-buddy/overview
- Project binding is recorded in the repository's `.firebaserc`. This is not evidence that Firestore, Authentication, or Android registration have been configured.

## Create your project
1. Project already created: open https://console.firebase.google.com/project/priscilla-buddy/overview.
2. Name it **Priscilla Buddy** (the project ID must be globally unique).
3. Disable Google Analytics for this project unless you explicitly need it.
4. Confirm **Spark** on the plan page. Do not attach billing.
5. Register an **Android app**, package name **`com.priscillabuddy.app`**. The Pixel and Fire can use the same Android app registration because they share the package name and signed build; we may later give development/release builds distinct registrations.
6. Enable **Authentication** and a parent sign-in provider. Email/password is a first-phase option; consider stronger MFA/passkeys later.
7. Create a **Cloud Firestore database** (Standard edition) in a US region and select production/locked rules.
8. Do NOT publish development test rules that permit every signed-in user to read every family.

## Configuration safety
- Firebase's `google-services.json` is an app configuration file rather than an administrator credential, but for this project keep it in your local development directory and do not commit it. The API key does NOT grant privileged database access; Firestore security rules and identity do.
- Never put service account JSON, Google Calendar OAuth client secret, refresh tokens, or Firebase Admin SDK credentials into the APK, GitHub, or GitHub Actions logs.
- The `firebase/firestore.rules` file is an **initial restricted ruleset for review**, not deployed. The repo's prior Supabase SQL file is historical design only and must NOT be deployed.
- The rules rely on family membership documents that can only be written by a trusted administrator. Parent invitations and child pairing are therefore not implemented by simply switching a UI profile.

## Planned first enrollment
1. Parent signs in on the Pixel with Firebase Authentication.
2. A trusted administrative path creates a family and owner membership. No self-elevation through app/client requests.
3. A child-device enrollment flow verifies a short-lived parent-approved pairing challenge and provisions a revocable restricted identity.
4. Only after Firestore rules tests and enrollment tests pass, enable cloud sync in the APK.
5. Test offline event creation, conflict reconciliation, cancelled recurring instances, account separation and revocation.

## Fire OS compatibility
Firebase's latest **core Authentication and Cloud Firestore Android SDKs do not require Google Play services**. Avoid dependency on Google Sign-In UI, Play Integrity, Firebase Cloud Messaging, and other SDKs requiring Play services on the unregistered Fire tablet. Firebase Authentication credentials and enrollment must be tested on the actual Fire OS 8 device.

## Security test matrix (must pass before connected release)
| Scenario | Expected |
|---|---|
| unauthenticated user reads a family | denied |
| stranger signed in to a different family | denied |
| child reads child-visible event | permitted |
| child reads private parent event | denied |
| child creates own child event | permitted |
| child edits parent-owned event | denied |
| child changes event owner/visibility/family/createdBy | denied |
| child changes protected routine templates | denied |
| parent manages events and routines for own family | permitted |
| disconnected tablet edits and reconnects | consistent, no duplicates |
| revoked child device tries to sync | denied |
| parent joins another household | no implicit cross-family reads |
| Google Calendar not connected | no Google Calendar access |
| Android screen-lock exit in child mode | verified by physical Fire OS testing |

**No public personal data should be entered until the connected release passes these tests.**

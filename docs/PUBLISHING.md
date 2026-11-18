# Publishing Silpa on Google Play

Everything that can be prepared in the code is done: release build with R8, App Bundle,
16 KB page-size compatible native libraries, target SDK 36, backups, version 1.0.0 (code 100),
store listing text and graphics in `fastlane/metadata/android/en-US/`, and a privacy policy.
The steps below need **your** accounts. Follow them in order.

## 1. Create your upload key (once, on your computer)
```
keytool -genkeypair -v -keystore silpa-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload
```
Keep the `.jks` file and passwords safe (password manager + backup). Then create
`keystore.properties` in the project root (it is git-ignored, never commit it):
```
storeFile=silpa-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX
admobBannerId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
```
With Play App Signing (the default) Google holds the real app-signing key; if you ever lose
this upload key, Play support can reset it.

## 2. AdMob
1. Create an account at admob.google.com → **Apps → Add app → Android** → name "Silpa".
2. Copy the **App ID** into `admobAppId`.
3. **Ad units → Banner** (adaptive) → copy the unit ID into `admobBannerId`.
4. **Privacy & messaging → GDPR / US states messages**: create and publish consent messages for Silpa
   (the app already shows them via Google's UMP SDK).
5. After the app is live, link it to the Play listing in AdMob and add an `app-ads.txt` file to your website.

Without these values the app shows Google's *test* ads (no revenue).

## 3. Build the release bundle
```
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`.
(`./gradlew assembleSideload` builds a debug-signed APK for installing on your own phone.)

## 4. Publish the privacy policy
Fill in `[DEVELOPER NAME]` and `[SUPPORT EMAIL]` in `docs/privacy-policy.html`, then host it at a public URL.
Easiest: GitHub → repository **Settings → Pages → Deploy from branch → `main` / `docs`**; the URL is
`https://<user>.github.io/<repo>/privacy-policy.html`.

## 5. Play Console
1. **Create app**: name *Silpa*, default language English (US), **App**, **Free**, accept the declarations.
2. **Monetize → Products → In-app products → Create**: Product ID **`silpa_pro`** (exact), name "Silpa Pro",
   description "Removes all ads, forever.", set a price, **Activate**. (Requires a payments profile.)
3. **Store presence → Main store listing**: paste `title.txt`, `short_description.txt`, `full_description.txt`;
   upload `images/icon.png`, `images/featureGraphic.png`, `images/phoneScreenshots/*`, `images/tenInchScreenshots/*`
   (the 10-inch shots can also be used for 7-inch). Category: **Art & Design**. Add contact email.
4. **Policy → App content** — answers for this build:

| Form | Answer |
|---|---|
| Privacy policy | Your hosted URL from step 4 |
| App access | All functionality available without special access |
| Ads | **Yes, my app contains ads** |
| Content rating (IARC) | Category: *All other app types*. No violence, sexuality, language, drugs or gambling *as app content*; users **cannot** interact or share content with each other inside the app; **digital purchases: yes**. Answer honestly for the bundled artwork and icon. |
| Target audience | 13+ age groups (not designed for children); app does not appeal primarily to children |
| News app / Government / Financial / Health | No |
| Advertising ID | **Yes**, used for **Advertising or marketing** (added by the AdMob SDK) |

**Data safety** (verify against Google's current AdMob "Data disclosure" page before submitting):
- Collects data: **Yes**. Encrypted in transit: **Yes**. Users can request deletion: data is on-device; ads data is managed via Google settings.
- **Device or other IDs** — collected *and shared* (Google AdMob) — Advertising, Analytics, Fraud prevention. Not optional for free users.
- **Approximate location** (from IP) — collected and shared — Advertising, Fraud prevention.
- **App activity → App interactions** — collected and shared — Advertising, Analytics.
- **App info and performance → Crash logs, Diagnostics** — collected and shared — Analytics, Fraud prevention.
- **Financial info → Purchase history** — collected, not shared — App functionality (Pro unlock).
- Photos, files and drawings are processed on device only → **not collected**.

5. **Testing → Closed testing**: create a track, upload `app-release.aab`, add testers.
   **New personal developer accounts must run a closed test with at least 12 testers for 14 continuous
   days** before Google lets you apply for production access.
6. **Production → Create release** → upload the same `.aab` (or promote from testing) → paste
   `changelogs/100.txt` → choose countries → **Send for review**.

## 6. Every later update
Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`, run `./gradlew bundleRelease`,
upload to a track, add release notes.

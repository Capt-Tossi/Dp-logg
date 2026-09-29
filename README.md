# DP Companion — first Android test build

Native Android app in Kotlin/Jetpack Compose. Records are kept on the device. Select a tour, tap **START DP**, then **STOP DP** and review the time/activity afterwards. Tour totals use the same logged-hour convention as the Excel tracker. Photos can be attached to a session, tour or CPD entry. **Export backup with photos** creates a ZIP containing data and images.

This is a personal working tool for preparing a signed NI/IMCA logbook entry, not a replacement for that logbook. All on-screen text is English.

## Build and test

Open the folder in Android Studio with Android SDK 36 and JDK 17. Run `testDebugUnitTest` and `assembleDebug`. The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A GitHub Actions workflow runs those checks and uploads the APK on each push.

GitHub Actions successfully ran `testDebugUnitTest` and `assembleDebug` for the first test build. Download the `dp-companion-debug-apk` artifact from the latest successful run, unzip it, and install `app-debug.apk` on Android.

## First-use notes

Create a tour in **Tours**; enter the signed-on date and the tour's operating mode. Leave the disembark date empty while still on board: DP days will be marked provisional and capped at the elapsed days on board. Enter the actual disembark date when the tour ends. Continuous DP requires a duty period. Active timing survives closing the app because the start timestamp is saved immediately. Use **Review session** after Stop to correct times. The previous times remain in correction history. Export a backup to preserve records and photos outside the phone.

The first build supports export but not restore. Use sample data for this first phone test. GitHub-generated debug APKs can be signed with different temporary keys between runs, so a later build may require uninstalling this test build, which removes its on-device data. Camera and installation flows still need a real-device test.

# DP Companion — first Android test build

Native Android app in Kotlin/Jetpack Compose. Records are kept on the device. Select a tour, tap **START DP**, then **STOP DP** and review the time/activity afterwards. Tour totals use the same logged-hour convention as the Excel tracker. Photos can be attached to a session, tour or CPD entry. **Export backup with photos** creates a ZIP containing data and images.

This is a personal working tool for preparing a signed NI/IMCA logbook entry, not a replacement for that logbook. All on-screen text is English.

## Build and test

Open the folder in Android Studio with Android SDK 36 and JDK 17. Run `testDebugUnitTest` and `assembleDebug`. The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A GitHub Actions workflow runs those checks and uploads the APK on each push.

The source was authored in an environment without an Android SDK or Gradle. No compiled APK or passing Android test result is claimed until an Android build runs successfully.

## First-use notes

Create a tour in **Tours**; enter the signed-on date and the tour's operating mode. Leave the disembark date empty while still on board: DP days will be marked provisional and capped at the elapsed days on board. Enter the actual disembark date when the tour ends. Continuous DP requires a duty period. Active timing survives closing the app because the start timestamp is saved immediately. Use **Review session** after Stop to correct times. The previous times remain in correction history. Export a backup to preserve records and photos outside the phone.

The first build supports export but not restore. Test with sample data before using it for live records.

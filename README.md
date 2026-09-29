# DP Companion — Android test build

Native Android app in Kotlin/Jetpack Compose. Records are kept on the device. Select a service period, tap **START DP**, then **STOP DP** and review the time/activity afterwards. Totals use the same logged-hour convention as the Excel tracker. Photos can be attached to a session, service period, vessel or CPD entry. **Export backup with photos** creates a ZIP containing data and images.

This is a personal working tool for preparing a signed NI/IMCA logbook entry, not a replacement for that logbook. All on-screen text is English.

## Build and test

Open the folder in Android Studio with Android SDK 36 and JDK 17. Run `testDebugUnitTest` and `assembleDebug`. The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A GitHub Actions workflow runs those checks and uploads the APK on each push.

GitHub Actions runs `testDebugUnitTest` and `assembleDebug` on each push. Download the `dp-companion-debug-apk` artifact from a successful run, unzip it, and install `app-debug.apk` on Android.

## First-use notes

Create a service period in **Sea Service**; enter the signed-on date and operating mode. Leave the disembarked date empty while still on board: DP days will be marked provisional and capped at the elapsed days on board. Enter the actual date when the service period ends. Continuous DP offers a watch-period field for reference; the IMCA logbook day estimate uses logged hours divided by two, capped at days aboard. Active timing survives closing the app because the start timestamp is saved immediately. Use **Review session** after Stop to correct times. The previous times remain in correction history. Export a backup to preserve records and photos outside the phone.

Version 0.3 adds **Me → Export reports / confirmation letter**. Select a vessel and choose a service-period summary, detailed DP session report, NI New Scheme draft confirmation letter or IMCA logbook draft confirmation letter. PDF and CSV are personal exports; the company must check and sign a confirmation letter before it is used. The New Scheme draft lists individual qualifying dates. Incomplete or overlapping records block letter drafts. Gross tonnage, date of birth and company name are entered in the export form; the vessel's GT can be saved under My Vessels. The ten DP-system dropdown examples are convenience choices, not a popularity ranking.

Version 0.2 adds **My Vessels**, a **Me** area with certificate and CPD / Training, and backup restore. Vessel records are templates for new tours; tours retain their own vessel snapshot and DP sessions if a vessel is removed. An old tour from v0.1 still displays its saved vessel; a tour linked to a removed catalog vessel displays "Missing vessel info" without removing its logs.

To carry test data from v0.1 to v0.2 if Android rejects installation over the old debug build: export a backup ZIP from v0.1, save it outside the app, uninstall v0.1, install v0.2, then use **Me → Restore backup**. Verify the tour, CPD and photos after import. GitHub-generated debug APKs can be signed with different temporary keys between runs. Use sample data until a stable release signing setup and device QA are complete.

The NI **Check validity** button opens the certificate result directly when certificate number and last name are saved. It uses the certificate link format provided by a user of the NI service, percent-encoding the combined identifier. If either value is missing, the official verification form opens instead. The NI site remains the authority for certificate status.

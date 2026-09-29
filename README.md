# DP Companion — Android test build

Native Android app in Kotlin/Jetpack Compose. Records are kept on the device. Select a service period, tap **START DP**, then **STOP DP** and review the time/activity afterwards. Totals use the same logged-hour convention as the Excel tracker. Photos can be attached to a session, service period, vessel or CPD entry. **Export backup with photos** creates a ZIP containing data and images.

This is a personal working tool for preparing a signed NI/IMCA logbook entry, not a replacement for that logbook. All on-screen text is English.

## Build and test

Open the folder in Android Studio with Android SDK 36 and JDK 17. Run `testDebugUnitTest` and `assembleDebug`. The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A GitHub Actions workflow runs those checks and uploads the APK on each push.

GitHub Actions runs `testDebugUnitTest` and `assembleDebug` on each push. Download the `dp-companion-debug-apk` artifact from a successful run, unzip it, and install `app-debug.apk` on Android.

## First-use notes

Create a service period in **Sea Service**; enter the signed-on date and operating mode. Leave the disembarked date empty while still on board: DP days will be marked provisional and capped at the elapsed days on board. Enter the actual date when the service period ends. Continuous DP offers a watch-period field for reference; the IMCA logbook day estimate uses logged hours divided by two, capped at days aboard. Active timing survives closing the app because the start timestamp is saved immediately. Use **Review session** after Stop to correct times. The previous times remain in correction history. Export a backup to preserve records and photos outside the phone.

Version 0.5.1 centers the app title, frames photos with rounded corners, shows saved DP-system details and totals together in Sea Service, and adds a shorter long press and review-screen Delete action with confirmation. Continuous watch hours can be confirmed with the keyboard Done action. The DPO certificate view shows the six-calendar-month NI application window and offers an optional offline local reminder. For shuttle tankers, a separate scheme choice and DP loading/mooring activities help label records; the app does not calculate qualifying offshore loading operations or produce an NI Shuttle Tanker confirmation letter.

Version 0.5.2 enlarges the launcher artwork and removes its white surrounding ring. No record format or DP calculation changed.

Version 0.5.3 lets a Sea Service period refresh or change its saved vessel details, and deletes a period with an explicit confirmation that includes its sessions and photos. Android Back follows the app screens, session cards show the start and stop time range, and the certificate card explains the renewal date on its own line. The bottom DP / Logg label, About changelog, and animated green START DP control are included. The stored data schema and DP calculations are unchanged.

Version 0.6.1 uses matching native green START and red STOP gradient controls with stopwatch symbols. A reserved status area keeps the control at the same vertical position as `Recording since` appears and disappears. Timestamps are saved on tap; a short pressed animation precedes the next view. The header has a blue gradient and the launcher uses separate adaptive icon background and foreground layers. The bottom label is `DP Logg` on one line, the DPO card shows its certificate number beside `Certificate`, and the sea background is a little more visible. Record format and DP calculations are unchanged.

The editable NI revalidation draft uses the current NI address, saved per-period vessel tonnage, a total DP-day figure and company verification/signature placeholders. New Scheme active dates remain a supporting breakdown; company records and the signed logbook must still be checked. The letter is not a certified NI document when exported.

**Upgrade from v0.5:** This CI debug APK has a different signing certificate from v0.5, so Android will not install it as an update. Export a backup ZIP with photos to external storage, check that the ZIP is present, uninstall v0.5, install v0.5.1, restore the ZIP in DPO, and verify sessions and photos. Keep the ZIP until you have checked the restored data. A stable private signing key is still needed for seamless future updates.

**Upgrade to v0.5.2:** CI debug signing is still not stable between builds. Export and verify a backup ZIP with photos outside the app before removing an earlier test version. Install v0.5.2, restore in DPO, and verify records and photos. Keep the ZIP until the restored data is checked.

**Upgrade to v0.5.3:** CI debug signing remains unstable. Export and verify a backup ZIP with photos outside the app, uninstall the earlier test version if Android reports a signature conflict, install v0.5.3, then restore in DPO. Verify service periods, sessions, and photos before deleting the ZIP.

**Upgrade to v0.6.1:** Export a backup ZIP with photos and verify that it is present outside the app. If Android rejects installation because the CI debug signature differs, uninstall the previous test build, install v0.6.1, restore in DPO, and check your sessions and photos before deleting the ZIP.

Version 0.4 added the maritime navigation and launcher icons, a subtle sea background, an About page, profile date of birth, sorted Sea Service cards with dates, explicit per-session review messages and EXIF-oriented vessel thumbnails. Backups retain compatibility with earlier schema versions.

**DPO → Export reports / confirmation letter** offers an editable Word document. The NI New Scheme and Old Scheme / Revalidation layouts use trip fields from NI's published Word templates; the New Scheme draft lists qualifying individual dates. The IMCA hours draft is labelled as a personal working format, not an NI template. The company must independently check vessel records and sign any letter. PDF remains available for summaries and detailed reports; session CSV remains available.

Version 0.3 added the initial vessel selection and PDF/CSV export. The DP-system dropdown examples are convenience choices, not a popularity ranking.

Version 0.2 adds **My Vessels**, a **Me** area with certificate and CPD / Training, and backup restore. Vessel records are templates for new tours; tours retain their own vessel snapshot and DP sessions if a vessel is removed. An old tour from v0.1 still displays its saved vessel; a tour linked to a removed catalog vessel displays "Missing vessel info" without removing its logs.

To carry test data from v0.1 to v0.2 if Android rejects installation over the old debug build: export a backup ZIP from v0.1, save it outside the app, uninstall v0.1, install v0.2, then use **Me → Restore backup**. Verify the tour, CPD and photos after import. GitHub-generated debug APKs can be signed with different temporary keys between runs. Use sample data until a stable release signing setup and device QA are complete.

The NI **Check validity** button opens the certificate result directly when certificate number and last name are saved. It uses the certificate link format provided by a user of the NI service, percent-encoding the combined identifier. If either value is missing, the official verification form opens instead. The NI site remains the authority for certificate status.

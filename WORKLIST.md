# DP Companion worklist

Version 0.5.1 was authorized and built. Record later requests here until the next explicit implementation request.

## Included in v0.5.1 source and CI build

- [x] Rename the bottom `Me` tab to `DPO`, the page heading to `DPO personal details`, and its back links to `Back to DPO`.
- [x] Dismiss status text such as `Backup restored` automatically after a few seconds.
- [x] Keep eight recent DP sessions on the main screen and add `View all sessions` to open the complete list for the selected service period, with a way back.
- [x] Show `Activity not set` only once on a session card.

## Phone checks for the next test build

- [ ] Check camera image orientation on the device, including vessel thumbnails and the in-app photo viewer. If images still appear sideways, investigate the camera file and EXIF metadata.
- [ ] Check that backup restore, photo replacement, photo deletion, and the full session list work with existing records.

## Later improvement

- [ ] Use a consistent private signing key for future test builds so updates can install over the existing app without an uninstall and restore. Keep the key out of the public repository.

## Feedback for the next update (not implemented)

- [ ] Allow editing the vessel associated with an existing Sea Service period. Show the chosen vessel and its details before saving, including how the period's saved vessel snapshot will change. Keep that period's sessions and documentation attached to the period. Editing a vessel in My Vessels should not silently rewrite historical periods.
- [ ] Allow deleting a Sea Service period with a confirmation that clearly states the effect on its sessions and attached photos. Do not delete the vessel from My Vessels or affect other periods. Make the action available from the selected period's detail view.
- [ ] Improve the certificate renewal card: give the renewal state its own clearly phrased line inside the card, with the date labelled in plain English (for example, `You can apply for renewal from 29 Mar 2026` or `Renewal applications are open`). Do not show an ambiguous `Open` beside an unexplained date. Retain the expiry date and countdown.
- [ ] Rename the first bottom navigation label from `DP` to two lines: `DP` / `Logg`. Check label and icon alignment on a narrow phone. Keep the rest of the app's English text as requested.
- [ ] Make Android system Back return to the previous in-app view, especially from Review session to the prior DP session list, matching the visible Back button. Handle nested DPO pages, View all sessions, image viewer and other detail states before leaving the app; only allow exit from the top-level screen. Preserve unsaved edits or warn before discarding them when relevant.

## v0.5.1 work

- [x] Continuous operating mode, `Watch period (hours)` input bug: the numeric keyboard stays active without an obvious Done/confirm action. Add a Done keyboard action that validates and saves the value, clears focus, and closes the keyboard; tapping outside should also dismiss it without losing a valid value. Keep an editable text draft while typing instead of converting every keystroke to Double and writing `0.0` for partial/invalid input (the current field does this), so decimals and corrections work. Show a clear inline validation message for empty/invalid/out-of-range values and test on the Android phone keyboard. Check other numeric entry fields for the same focus behavior.
- [x] Buoy-loading / shuttle-tanker workflow (scheme label and activity choices only): review whether to add an explicit Shuttle tanker / offshore loading scheme choice for a service period, separate from the existing Normal / Continuous hours calculation. Vessel type already includes `Shuttle tanker / buoy loading`; add fitting session activities such as `Offshore loading (on DP)` and potentially `Position mooring / TAM (on DP)` only with clear definitions. NI counts offshore loading operations for the Shuttle Tanker Restricted scheme separately from Offshore DP sea-time days, and says loading without DP in use does not count as a qualifying operation (https://nialexisplatform.kayako.com/article/240-what-is-the-definition-of-sea-time-for-the-shuttle-tanker-scheme). NI also has distinct POSMOOR/TAM conditions (https://nialexisplatform.kayako.com/article/93-definitions-new-offshore-scheme-for-limited-unlimited-and-unclassed-dp-certificates). The 0.5.1 build does not automatically convert mooring/loading activity to qualifying shuttle operations; a full operation register and NI shuttle export remain future work.
- [x] Top app bar: center the `DP Companion` title horizontally on all main tabs and detail screens. Keep the title clear of Android status icons and any future navigation or action buttons, including on narrow phones.
- [x] Sea Service detail layout: below the selected vessel name and existing IMO / vessel type / DP class line, show `DP system: …`, then immediately below show `X logged DP hours · Y DP days`. Move this totals line from beneath Operating mode; keep any provisional or calculation warning next to the totals where it is visible. Keep the vessel thumbnail at the right. Save DP system on each service-period snapshot when a period is created, so later vessel edits/removal do not silently change historical details; for older records, display the current linked vessel value only when available and handle missing data clearly. The period selector should continue to show vessel name and dates.
- [x] Photo presentation throughout the app: display all photo thumbnails and previews (vessels, certificate, DP checklists, logbook pages, CPD / training and other documentation) in a consistent subtle frame with rounded corners. Clip each image to the same radius; preserve aspect ratio and use an intentional crop only for small thumbnails. In the full-image viewer, show the complete image without cutting off document edges.
- [x] NI confirmation letter export: align the editable Word draft with NI's July 2026 Offshore Revalidation template and published requirements (https://nialexisplatform.kayako.com/article/114-confirmation-letters-offshore-revalidation). Correct NI address to 200B Lambeth Road, London SE1 7JY; use a generic revalidation title rather than calling the letter itself New Scheme; retain vessel/GRT/IMO/DP class/service dates/DP days/rank, explicitly state the total claimed DP days, and include an independently verified company statement. Provide room for original company letterhead/contact details and an authorised Operations Manager/Marine Superintendent or equivalent operational signatory with name, position, direct email, ink signature/stamp and date. Keep the applicant-generated draft visibly unsigned. The old NI New Offshore Scheme sample (https://www.nialexisplatform.org/media/987885/confirmation-letter-template-new-offshore-scheme-and-revalidation-august-2018-example.pdf) lists individual active/passive dates, while the July 2026 revalidation template does not require that list; use an optional date annex only if reconciled with the claimed days and company records. Check export data for placeholders, vessel-specific GRT, and title/rank/capacity. Block or flag mismatches between the DP days in each table row and qualifying individual dates, including continuous-hour/IMCA records. Test a multi-service-period letter and a two-vessel case before releasing.
- [x] Session card: after a shorter long press (target about 1 second, to confirm during implementation), reveal the trash action inside a dark red filled button with clear contrast even on red overlap cards. Keep the existing delete confirmation.
- [x] Review session: place Delete below Save session, on the same row as Back (aligned to the right). It must use the same confirmation and delete the selected session only when confirmed.
- [x] Certificate renewal: calculate the opening of the renewal window as six calendar months before the entered expiry date. In the DPO certificate card and Certificate screen, clearly show `Renewal window open` once that date arrives, alongside the expiry date and days remaining. Keep the existing expired state distinct. NI Help/FAQ confirms the online revalidation application cannot be started more than six months before certificate expiry (for example, 23 July expiry opens 23 January): https://nialexisplatform.kayako.com/article/408-can-i-revalidate-my-certificate-before-the-expiry-date . This is the application opening date, not a deadline or a guarantee that all renewal requirements are met.
- [x] Optional offline Android reminder for the certificate renewal window: schedule a local notification for the opening date, request notification permission only when the user enables reminders, and update/cancel the reminder when the expiry date changes or the certificate is renewed. The app must remain offline; no server push service is needed.

## Remaining follow-up

- [ ] Design and verify a separate Shuttle Tanker operation register and NI export using the applicable NI scheme; do not infer qualifying operations from individual DP sessions.
- [ ] Complete the listed phone checks for camera orientation, backup restore and image replacement/deletion on a physical device.

Add each new request here with its intended behavior and status before implementation.

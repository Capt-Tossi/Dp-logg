# DP Companion worklist

Record new feedback here. Do not implement further changes or prepare another APK until Torstein explicitly asks to start.

## Implemented in source, awaiting next authorized APK

- [x] Rename the bottom `Me` tab to `DPO`, the page heading to `DPO personal details`, and its back links to `Back to DPO`.
- [x] Dismiss status text such as `Backup restored` automatically after a few seconds.
- [x] Keep eight recent DP sessions on the main screen and add `View all sessions` to open the complete list for the selected service period, with a way back.
- [x] Show `Activity not set` only once on a session card.

## Phone checks for the next test build

- [ ] Check camera image orientation on the device, including vessel thumbnails and the in-app photo viewer. If images still appear sideways, investigate the camera file and EXIF metadata.
- [ ] Check that backup restore, photo replacement, photo deletion, and the full session list work with existing records.

## Later improvement

- [ ] Use a consistent private signing key for future test builds so updates can install over the existing app without an uninstall and restore. Keep the key out of the public repository.

## New feedback — awaiting authorization to implement

- [ ] Session card: after a shorter long press (target about 1 second, to confirm during implementation), reveal the trash action inside a dark red filled button with clear contrast even on red overlap cards. Keep the existing delete confirmation.
- [ ] Review session: place Delete below Save session, on the same row as Back (aligned to the right). It must use the same confirmation and delete the selected session only when confirmed.
- [ ] Certificate renewal: calculate the opening of the renewal window as six calendar months before the entered expiry date. In the DPO certificate card and Certificate screen, clearly show `Renewal window open` once that date arrives, alongside the expiry date and days remaining. Keep the existing expired state distinct. NI Help/FAQ confirms the online revalidation application cannot be started more than six months before certificate expiry (for example, 23 July expiry opens 23 January): https://nialexisplatform.kayako.com/article/408-can-i-revalidate-my-certificate-before-the-expiry-date . This is the application opening date, not a deadline or a guarantee that all renewal requirements are met.
- [ ] Optional offline Android reminder for the certificate renewal window: schedule a local notification for the opening date, request notification permission only when the user enables reminders, and update/cancel the reminder when the expiry date changes or the certificate is renewed. The app must remain offline; no server push service is needed.

Add each new request here with its intended behavior and status before implementation.

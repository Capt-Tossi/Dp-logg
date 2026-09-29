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

## New feedback

Add each new request here with its intended behavior and status before implementation.

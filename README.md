# Division 2 Stash Scanner — Xbox 4K profile (v0.2)

Android Studio starter project. This is source code, not a compiled APK.

## What changed in v0.2
- Xbox / 4K capture profile labels and workflow guidance.
- Handles high-resolution video frames, reducing only frames wider than 2560 px before OCR to control memory use while retaining readable UI detail.
- Samples approximately once per second.
- Requires a detected screen group to span at least five seconds before exporting it as a candidate item.
- Adds a review screen: tap a candidate to correct the item name and OCR text, or delete a false detection.
- Exports a CSV with candidate slot, brand/set, talent, armor text, timestamps, and complete OCR text.

## Known limitations
- No Xbox capture-specific visual layout model has been trained yet; the app reads all visible text rather than understanding every icon or gear panel.
- OCR-derived item name, slot, brand, and talent are guesses. Review them before relying on the spreadsheet.
- Similarity grouping is heuristic. A UI animation, tooltip, or cursor movement can split or merge item groups.
- This version does not yet export native XLSX, recognize attribute icons/roll bars, or calculate build quality.
- The app scans the whole selected clip. A 30–60 second clip is recommended; longer clips take proportionally longer.

## Build
Open this folder in Android Studio, install SDK 35, sync Gradle, connect an Android device (Android 8.0+), then press Run. A current Android Studio installation will download the required Gradle and ML Kit dependencies.

## Recommended Xbox 4K capture procedure
1. Record at 4K using the Xbox capture settings available to your console/capture device.
2. Open the stash and inspect one item at a time.
3. Keep the full item details panel visible for 6–7 seconds where possible. Five seconds is the minimum filter, not a guarantee that every frame will be decoded at an exact one-second interval.
4. Avoid opening comparison popups or moving over other items during the capture.
5. Import the video, scan, tap each candidate row to correct its name/OCR, and export CSV.

## Next development milestone
The next major step is to obtain one representative Xbox 4K sample clip (ideally 30–60 seconds with 5–7 seconds per item). That will let us tune item-panel detection, screen-region cropping, transition recognition, and Division 2-specific field parsing against the actual capture UI.

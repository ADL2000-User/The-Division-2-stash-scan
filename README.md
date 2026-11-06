# Division 2 Stash Scanner — Xbox 4K / 1440p / 1080p (v0.4)

Android Studio starter project. This is source code, not a compiled APK.

## What changed in v0.4 (tuned on a real 1080p clip)
- **Layout-aware detection.** Instead of reading the whole screen, the app finds the orange highlighted row in the stash list, reads only that row for the item name, and reads only the details panel on the right. Character stats, button hints and the other list rows no longer leak into results. Crop positions are fractions of the frame, so 4K/1440p/1080p captures all work.
- **Item grouping by highlighted row.** A new item starts when the highlight moves or the highlighted name changes, so it still works when the highlight stays in place while the list scrolls. Frames taken mid-movement are skipped, one-frame misreads are absorbed, and stale panels left over from the previous item are dropped.
- **Per-frame voting.** Every frame of an item is parsed on its own, then the most common value wins for each field. This lets you scroll the details panel while hovering an item, and fields from the top and bottom of the panel get merged.
- **New fields:** RPM, magazine size, attributes with values, augment, expertise/proficiency and weapon mods. "Equipped Items" text is excluded. OCR fixes cover "+" read as 1/4, dropped decimals and comma decimals ("90,25k").
- Added the Prototype rarity, a weapon type fallback from the core damage attribute, a "Has attribute" filter, and RPM/MAG sorts.
- Clip test result: 15/15 items found with correct names and time ranges. 13/15 have the right weapon type, all have a rarity, and most have DMG/RPM/MAG and talent.

## Recommended capture procedure (v0.4)
1. Record at 1080p or higher with the stash open (the weapons or gear tab).
2. Move the highlight onto an item and keep it there for 5–8 seconds.
3. While it is highlighted, scroll the details panel from top to bottom so talents, attributes and mods come into view.
4. Then move to the next item. Avoid comparison popups.

## What changed in v0.3
- **1080p (and 1440p) support.** Resolution is read from the video and a capture profile is chosen automatically (4K, 1440p, 1080p, 720p best-effort), or pick one manually. 1080p frames are upscaled 1.5× before OCR so small tooltip text stays readable; 4K frames are still reduced to 2560 px wide to save memory.
- Optional contrast boost (grayscale + contrast, on by default for 1080p/720p), 0.5-second sampling, and an adjustable minimum time on each item (2–5 s).
- **Division 2 field detection:** category (weapon/gear), gear slot or weapon type, rarity (Exotic, Named, Gear Set, High-End…), brand, gear set, talent (incl. "Perfect" talents), core attribute (Weapon Damage / Armor / Skill Tier) and main stat value. Attribute lines like "+12% Assault Rifle Damage" are ignored when detecting the slot.
- **Filters:** text search plus category, slot/type, rarity, brand/gear set, talent, core attribute, tag, duplicates, favorites, needs-review and has-talent.
- **Organization:** sort by video order, name, slot, brand/set, rarity, main stat, tag or talent; group by any of those (or source video) with counts; rarity color strips; duplicate counts (×N) and one-tap "Remove repeats".
- **Tags & review:** tag items Keep / Build piece / Recalibrate / Sell, bulk-tag everything currently shown, long-press to favorite, full edit form with "Re-detect fields from text" and notes.
- The list is saved between sessions, and new recordings can be added to it, so a large stash can be built up across several clips.
- CSV export of the shown (filtered + sorted) items or all items, with the new columns.

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

package com.example.division2stashscanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.coroutines.resume

/** Capture resolution profiles. The stash layout is the same at every resolution; only OCR scaling differs. */
enum class CaptureProfile(val label: String) {
    AUTO("Auto-detect from video"),
    UHD_4K("4K (2160p)"),
    QHD_1440("1440p"),
    FHD_1080("1080p"),
    HD_720("720p (best effort)");

    companion object {
        fun detect(width: Int, height: Int): CaptureProfile {
            val shortSide = minOf(width, height)
            return when {
                shortSide >= 2000 -> UHD_4K
                shortSide >= 1400 -> QHD_1440
                shortSide >= 1000 -> FHD_1080
                shortSide > 0 -> HD_720
                else -> FHD_1080
            }
        }
    }
}

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PICK_VIDEO = 1001
        private const val SAVE_CSV = 1002
        private const val ALL = "All"
        private val BG = 0xFF15191D.toInt()
        private val PANEL = 0xFF232A31.toInt()
        private val PANEL_DARK = 0xFF1B2126.toInt()
        private val ACCENT = 0xFFE87524.toInt()
        private val SUBTLE = 0xFFD0D5D9.toInt()
        private val MUTED = 0xFF8E979F.toInt()
        private val WARN = 0xFFFFC58A.toInt()

        private val SORTS = listOf("Video order", "Name (A–Z)", "Slot / type", "Brand / set", "Rarity (Exotic first)",
            "Total damage / main stat (high–low)", "Tag", "Talent", "RPM (high–low)", "Magazine (high–low)")
        private val GROUPS = listOf("No grouping", "Category", "Slot / type", "Brand / set", "Rarity", "Talent",
            "Core attribute", "Tag", "Source video")
        private val DUPES = listOf("Show all copies", "Hide repeats", "Only repeats")
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val items = mutableListOf<ScannedItem>()
    private lateinit var store: ItemStore

    private lateinit var status: TextView
    private lateinit var videoInfo: TextView
    private lateinit var summary: TextView
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var filterPanel: LinearLayout
    private lateinit var filterToggle: Button

    private lateinit var spProfile: Spinner
    private lateinit var spStep: Spinner
    private lateinit var spHold: Spinner
    private lateinit var cbEnhance: CheckBox

    private lateinit var etSearch: EditText
    private lateinit var spCategory: Spinner
    private lateinit var spSlot: Spinner
    private lateinit var spRarity: Spinner
    private lateinit var spBrand: Spinner
    private lateinit var spTalent: Spinner
    private lateinit var spCore: Spinner
    private lateinit var spTag: Spinner
    private lateinit var spAttr: Spinner
    private lateinit var spDupes: Spinner
    private lateinit var spSort: Spinner
    private lateinit var spGroup: Spinner
    private lateinit var cbFavorites: CheckBox
    private lateinit var cbNeedsReview: CheckBox
    private lateinit var cbHasTalent: CheckBox

    private val spinnerOptions = mutableMapOf<Spinner, List<String>>()
    private var selectedUri: Uri? = null
    private var selectedLabel = ""
    private var detectedWidth = 0
    private var detectedHeight = 0
    private var pendingCsv = ""
    private var nextId = System.currentTimeMillis()

    // ---------------------------------------------------------------- UI construction

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float, color: Int = android.graphics.Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun section(title: String) = label(title, 13f, ACCENT, bold = true).apply { setPadding(0, dp(14), 0, dp(6)) }

    private fun spinner(options: List<String>, onChange: () -> Unit = { renderItems() }): Spinner =
        Spinner(this).apply {
            setOptions(this, options)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = onChange()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

    /** Replaces spinner options while keeping the current selection where possible. */
    private fun setOptions(sp: Spinner, options: List<String>) {
        if (spinnerOptions[sp] == options) return
        val current = sp.selectedItem as? String
        spinnerOptions[sp] = options
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        val i = options.indexOf(current)
        sp.setSelection(if (i >= 0) i else 0, false)
    }

    private fun sel(sp: Spinner) = sp.selectedItem as? String ?: ALL

    private fun labeledRow(name: String, view: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(label(name, 13f, SUBTLE), LinearLayout.LayoutParams(0, -2, 0.38f))
        addView(view, LinearLayout.LayoutParams(0, -2, 0.62f))
    }

    private fun buttonRow(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
    }

    private fun smallButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; isAllCaps = false; setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ItemStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(BG)
        }
        root.addView(label("DIVISION 2\nSTASH SCANNER", 25f, bold = true))
        root.addView(label("XBOX • 4K / 1440p / 1080p CAPTURE • v0.4", 12f, 0xFFFFB36B.toInt()).apply { setPadding(0, dp(6), 0, dp(10)) })
        root.addView(label("Record your stash: highlight an item, scroll its details panel for 5+ seconds, then move to the next item. " +
            "4K, 1440p and 1080p recordings are supported; the resolution is detected automatically.", 14f, SUBTLE))

        // --- Capture & scan
        root.addView(section("CAPTURE & SCAN"))
        root.addView(Button(this).apply {
            text = "1  •  Choose stash recording"
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
                }, PICK_VIDEO)
            }
        })
        videoInfo = label("No recording selected.", 13f, MUTED).apply { setPadding(0, dp(4), 0, dp(4)) }
        root.addView(videoInfo)

        spProfile = spinner(CaptureProfile.values().map { it.label }) { updateProfileHint() }
        root.addView(labeledRow("Resolution profile", spProfile))
        spStep = spinner(listOf("Every 1 second", "Every 0.5 seconds (slower, more accurate)")) {}
        root.addView(labeledRow("Frame sampling", spStep))
        spHold = spinner(listOf("5 seconds", "4 seconds", "3 seconds", "2 seconds")) {}
        root.addView(labeledRow("Min. time on item", spHold))
        cbEnhance = CheckBox(this).apply {
            text = "Boost text contrast (try if text is misread)"; setTextColor(SUBTLE); textSize = 13f
        }
        root.addView(cbEnhance)

        scanButton = Button(this).apply { text = "2  •  Analyze video"; isEnabled = false }
        root.addView(scanButton)
        status = label("", 13f, WARN).apply { setPadding(0, dp(8), 0, dp(4)) }
        root.addView(status)

        // --- Filters & organization
        filterToggle = Button(this).apply {
            text = "Hide filters & organization ▲"; isAllCaps = false
            setOnClickListener {
                val show = filterPanel.visibility != View.VISIBLE
                filterPanel.visibility = if (show) View.VISIBLE else View.GONE
                text = if (show) "Hide filters & organization ▲" else "Show filters & organization ▼"
            }
        }
        root.addView(section("FILTERS & ORGANIZATION"))
        root.addView(filterToggle)

        filterPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PANEL_DARK)
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        etSearch = EditText(this).apply {
            hint = "Search name, brand, talent, notes or OCR text"
            setTextColor(android.graphics.Color.WHITE); setHintTextColor(MUTED); textSize = 14f
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = renderItems()
            })
        }
        filterPanel.addView(etSearch)
        spCategory = spinner(listOf(ALL) + Catalog.categories + UNKNOWN)
        spSlot = spinner(listOf(ALL) + Catalog.allSlots + UNKNOWN)
        spRarity = spinner(listOf(ALL) + Catalog.rarities + UNKNOWN)
        spBrand = spinner(listOf(ALL))
        spTalent = spinner(listOf(ALL))
        spCore = spinner(listOf(ALL) + Catalog.cores + UNKNOWN)
        spTag = spinner(listOf(ALL) + Catalog.tags)
        spAttr = spinner(listOf(ALL))
        spDupes = spinner(DUPES)
        filterPanel.addView(labeledRow("Category", spCategory))
        filterPanel.addView(labeledRow("Slot / weapon type", spSlot))
        filterPanel.addView(labeledRow("Rarity", spRarity))
        filterPanel.addView(labeledRow("Brand / gear set", spBrand))
        filterPanel.addView(labeledRow("Talent", spTalent))
        filterPanel.addView(labeledRow("Core attribute", spCore))
        filterPanel.addView(labeledRow("Has attribute", spAttr))
        filterPanel.addView(labeledRow("Tag", spTag))
        filterPanel.addView(labeledRow("Duplicates", spDupes))
        cbFavorites = CheckBox(this).apply { text = "Favorites only"; setTextColor(SUBTLE); setOnCheckedChangeListener { _, _ -> renderItems() } }
        cbNeedsReview = CheckBox(this).apply { text = "Needs review only"; setTextColor(SUBTLE); setOnCheckedChangeListener { _, _ -> renderItems() } }
        cbHasTalent = CheckBox(this).apply { text = "Has a detected talent"; setTextColor(SUBTLE); setOnCheckedChangeListener { _, _ -> renderItems() } }
        filterPanel.addView(cbFavorites); filterPanel.addView(cbNeedsReview); filterPanel.addView(cbHasTalent)

        spSort = spinner(SORTS)
        spGroup = spinner(GROUPS)
        filterPanel.addView(label("Organize", 13f, ACCENT, bold = true).apply { setPadding(0, dp(10), 0, dp(4)) })
        filterPanel.addView(labeledRow("Sort by", spSort))
        filterPanel.addView(labeledRow("Group by", spGroup))
        filterPanel.addView(buttonRow(
            smallButton("Reset filters") { resetFilters() },
            smallButton("Tag shown…") { tagShown() }
        ))
        root.addView(filterPanel)

        // --- Results
        root.addView(section("ITEMS"))
        summary = label("", 13f, SUBTLE).apply { setPadding(0, 0, 0, dp(6)) }
        root.addView(summary)
        root.addView(buttonRow(
            smallButton("3 • Export CSV") { exportCsv() },
            smallButton("Remove repeats") { removeRepeats() },
            smallButton("Clear all") { clearAll() }
        ))
        root.addView(label("Tap an item to review or edit it. Long-press to favorite it.", 12f, MUTED).apply { setPadding(0, dp(4), 0, dp(8)) })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })

        scanButton.setOnClickListener { onScanClicked() }

        items.addAll(store.load())
        nextId = maxOf(nextId, (items.maxOfOrNull { it.id } ?: 0L) + 1)
        refreshDynamicFilters()
        renderItems()
        if (items.isNotEmpty()) status.text = "Loaded ${items.size} saved items from your last session."
    }

    // ---------------------------------------------------------------- Video selection & profile

    private fun chosenProfile(): CaptureProfile {
        val p = CaptureProfile.values()[spProfile.selectedItemPosition.coerceAtLeast(0)]
        return if (p == CaptureProfile.AUTO) CaptureProfile.detect(detectedWidth, detectedHeight) else p
    }

    private fun updateProfileHint() {
        if (selectedUri == null) return
        val p = chosenProfile()
        cbEnhance.isChecked = p == CaptureProfile.HD_720
        val scaleNote = when (p) {
            CaptureProfile.UHD_4K -> "item panel read at native size"
            CaptureProfile.QHD_1440 -> "item panel upscaled 1.5× for OCR"
            CaptureProfile.FHD_1080 -> "item panel upscaled 2× so small text is readable"
            CaptureProfile.HD_720 -> "item panel upscaled 2.5× (low detail, review results carefully)"
            CaptureProfile.AUTO -> ""
        }
        val res = if (detectedWidth > 0) "${detectedWidth}×${detectedHeight}" else "unknown resolution"
        videoInfo.text = "$selectedLabel\n$res → ${p.label}: $scaleNote"
    }

    private fun onVideoSelected(uri: Uri) {
        selectedUri = uri
        selectedLabel = displayName(uri)
        scanButton.isEnabled = true
        videoInfo.text = "Reading video details…"
        scope.launch {
            val (w, h) = withContext(Dispatchers.IO) { readResolution(uri) }
            detectedWidth = w; detectedHeight = h
            updateProfileHint()
            status.text = "Recording selected. Tap Analyze video."
        }
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Selected video"
    } catch (e: Exception) { "Selected video" }

    private fun readResolution(uri: Uri): Pair<Int, Int> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            w to h
        } catch (e: Exception) { 0 to 0 } finally { r.release() }
    }

    // ---------------------------------------------------------------- Scanning

    private fun onScanClicked() {
        if (selectedUri == null) return
        if (items.isEmpty()) { runScan(replace = false); return }
        AlertDialog.Builder(this)
            .setTitle("You already have ${items.size} items")
            .setMessage("Add this recording's items to your current list, or replace the list?")
            .setPositiveButton("Add to list") { _, _ -> runScan(replace = false) }
            .setNeutralButton("Replace list") { _, _ -> runScan(replace = true) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun runScan(replace: Boolean) {
        val uri = selectedUri ?: return
        val profile = chosenProfile()
        val stepMs = if (spStep.selectedItemPosition == 1) 500L else 1000L
        val holdMs = (5 - spHold.selectedItemPosition.coerceAtLeast(0)) * 1000L
        val enhance = cbEnhance.isChecked
        val source = selectedLabel
        scanButton.isEnabled = false
        scope.launch {
            status.text = "Preparing ${profile.label} video…"
            val found = try {
                withContext(Dispatchers.IO) { scanVideo(uri, profile, stepMs, holdMs, enhance, source) }
            } catch (e: Exception) {
                status.text = "Scan failed: ${e.message ?: e.javaClass.simpleName}"
                scanButton.isEnabled = true
                return@launch
            }
            if (replace) items.clear()
            items.addAll(found)
            store.save(items)
            refreshDynamicFilters()
            renderItems()
            status.text = "Scan complete: ${found.size} item screens found in ${profile.label} video. Total list: ${items.size}."
            scanButton.isEnabled = true
        }
    }

    private suspend fun scanVideo(
        uri: Uri, profile: CaptureProfile, stepMs: Long, holdMs: Long, enhance: Boolean, source: String
    ): List<ScannedItem> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val frames = mutableListOf<FrameRead>()
        try {
            retriever.setDataSource(this@MainActivity, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) return@withContext emptyList<ScannedItem>()
            var t = 0L
            var n = 0
            var lastBandTop = -1
            while (t <= durationMs && isActive) {
                val frame = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val read = readFrame(frame, t, profile, enhance, recognizer, lastBandTop)
                    frame.recycle()
                    if (read.bandTop >= 0) lastBandTop = read.bandTop
                    frames.add(read)
                }
                n++
                if (n % 2 == 0) {
                    val done = t / 1000; val total = durationMs / 1000
                    val found = frames.count { it.rowName.isNotBlank() }
                    withContext(Dispatchers.Main) {
                        status.text = "Scanning ${profile.label}: ${done}s of ${total}s… (item highlight found in $found frames)"
                    }
                }
                t += stepMs
            }
        } finally {
            retriever.release(); recognizer.close()
        }
        ScanLogic.group(frames, stepMs, holdMs).map { g ->
            val item = ScannedItem(nextId++, source, g.startMs, g.endMs, g.panelText)
            item.name = g.name
            ItemParser.apply(item, keepName = g.name.isNotBlank(), panels = g.panels)
            item
        }
    }

    /**
     * Reads one frame of the stash screen: finds the highlighted row in the item list, reads its name,
     * and reads the item details panel. Only these two areas are read, so the character stats, the rest
     * of the list and the button hints no longer pollute the item text.
     */
    private suspend fun readFrame(
        frame: Bitmap, ms: Long, profile: CaptureProfile, enhance: Boolean,
        recognizer: com.google.mlkit.vision.text.TextRecognizer, lastBandTop: Int
    ): FrameRead {
        val w = frame.width; val h = frame.height
        // Crops are small, so upscale them for OCR: 1080p ×2, 1440p ×1.5, 4K ×1.
        val scale = (2160f / h).coerceIn(1f, 2.5f) * if (profile == CaptureProfile.HD_720) 1.2f else 1f
        val top = (StashLayout.TOP * h).toInt()
        val bottom = (StashLayout.BOTTOM * h).toInt()

        // 1) Highlighted row in the list column.
        val lx = (StashLayout.LIST_LEFT * w).toInt(); val lw = (StashLayout.LIST_RIGHT * w).toInt() - lx
        val lh = bottom - top
        val px = IntArray(lw * lh)
        frame.getPixels(px, 0, lw, lx, top, lw, lh)
        val ratios = FloatArray(lh) { y ->
            var hit = 0; var cnt = 0
            var x = 0
            while (x < lw) {
                val c = px[y * lw + x]
                if (StashLayout.isHighlight((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)) hit++
                cnt++; x += 4
            }
            hit.toFloat() / cnt
        }
        val band = StashLayout.findBand(ratios, (StashLayout.MIN_BAND * h).toInt())
        var rowName = ""
        var bandTop = -1
        var moving = false
        if (band != null) {
            bandTop = top + band[0]
            moving = lastBandTop >= 0 && kotlin.math.abs(bandTop - lastBandTop) > StashLayout.MOVE_TOLERANCE * h
            val nx = (StashLayout.NAME_LEFT * w).toInt()
            val ny = (bandTop + StashLayout.NAME_TOP_OFFSET * h).toInt()
            val nw = (StashLayout.NAME_RIGHT * w).toInt() - nx
            val nh = (StashLayout.NAME_HEIGHT * h).toInt().coerceAtMost(h - ny)
            if (nw > 0 && nh > 0) {
                val nameCrop = crop(frame, nx, ny, nw, nh, scale * 1.5f, enhance = false)
                rowName = ScanLogic.cleanRowName(recognizer.process(InputImage.fromBitmap(nameCrop, 0)).awaitText()
                    ?.text.orEmpty())
                nameCrop.recycle()
            }
        }

        // 2) Item details panel.
        val px0 = (StashLayout.PANEL_LEFT * w).toInt()
        val panelCrop = crop(frame, px0, top, (StashLayout.PANEL_RIGHT * w).toInt() - px0, bottom - top, scale, enhance)
        val panelText = recognizer.process(InputImage.fromBitmap(panelCrop, 0)).awaitText()?.let { orderedLines(it) }.orEmpty()
        panelCrop.recycle()
        return FrameRead(ms, rowName, bandTop, moving, panelText)
    }

    /** ML Kit returns blocks in arbitrary order; sort lines top-to-bottom and join lines on the same row. */
    private fun orderedLines(text: Text): String {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return text.text
        val sorted = lines.sortedBy { it.boundingBox!!.centerY() }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (l in sorted) {
            val box = l.boundingBox!!
            val row = rows.lastOrNull()
            val ref = row?.first()?.boundingBox
            if (row != null && ref != null && kotlin.math.abs(box.centerY() - ref.centerY()) < ref.height() * 0.55f) row.add(l)
            else rows.add(mutableListOf(l))
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.boundingBox!!.left }.joinToString("  ") { it.text } }
    }

    /** Crops a region and scales it, optionally converting to high-contrast grayscale. */
    private fun crop(src: Bitmap, x: Int, y: Int, w: Int, h: Int, scale: Float, enhance: Boolean): Bitmap {
        val cx = x.coerceIn(0, src.width - 1); val cy = y.coerceIn(0, src.height - 1)
        val cw = w.coerceAtMost(src.width - cx).coerceAtLeast(1); val ch = h.coerceAtMost(src.height - cy).coerceAtLeast(1)
        val ow = (cw * scale).toInt().coerceAtLeast(1); val oh = (ch * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (enhance) {
            val c = 1.6f
            val t = (-0.5f * c + 0.5f) * 255f
            val m = ColorMatrix().apply { setSaturation(0f) }
            m.postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
            paint.colorFilter = ColorMatrixColorFilter(m)
        }
        Canvas(out).drawBitmap(src, Rect(cx, cy, cx + cw, cy + ch), RectF(0f, 0f, ow.toFloat(), oh.toFloat()), paint)
        return out
    }

    private suspend fun com.google.android.gms.tasks.Task<Text>.awaitText(): Text? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }

    // ---------------------------------------------------------------- Filtering, sorting, grouping

    private fun refreshDynamicFilters() {
        setOptions(spBrand, listOf(ALL) + items.map { it.brandOrSet }.distinct()
            .sortedWith(compareBy({ it == NONE_DETECTED }, { it })))
        setOptions(spTalent, listOf(ALL) + items.map { it.talentOrNone }.distinct()
            .sortedWith(compareBy({ it == NONE_DETECTED }, { it })))
        setOptions(spAttr, listOf(ALL) + items.flatMap { it.attributeNames }.distinct().sorted())
    }

    private fun filtered(): List<ScannedItem> {
        val q = etSearch.text.toString().trim().lowercase(Locale.US)
        val cat = sel(spCategory); val slot = sel(spSlot); val rar = sel(spRarity); val brand = sel(spBrand)
        val talent = sel(spTalent); val core = sel(spCore); val tag = sel(spTag); val dupes = sel(spDupes)
        val attr = sel(spAttr)
        val counts = items.groupingBy { it.dupKey }.eachCount()
        val seen = mutableSetOf<String>()

        val base = items.filter { i ->
            (q.isEmpty() || listOf(i.name, i.brandOrSet, i.talent, i.notes, i.rawText, i.slot, i.attributes, i.mods, i.augment)
                .any { it.lowercase(Locale.US).contains(q) }) &&
                (cat == ALL || i.category == cat) &&
                (slot == ALL || i.slot == slot) &&
                (rar == ALL || i.rarity == rar) &&
                (brand == ALL || i.brandOrSet == brand) &&
                (talent == ALL || i.talentOrNone == talent) &&
                (core == ALL || i.core == core) &&
                (tag == ALL || i.tag == tag) &&
                (attr == ALL || attr in i.attributeNames) &&
                (!cbFavorites.isChecked || i.favorite) &&
                (!cbNeedsReview.isChecked || !i.reviewed) &&
                (!cbHasTalent.isChecked || i.talent.isNotBlank()) &&
                (dupes != DUPES[2] || (counts[i.dupKey] ?: 0) > 1)
        }
        val sorted = when (sel(spSort)) {
            SORTS[1] -> base.sortedBy { it.displayName.lowercase(Locale.US) }
            SORTS[2] -> base.sortedWith(compareBy({ Catalog.slotRank(it.slot) }, { it.displayName }))
            SORTS[3] -> base.sortedWith(compareBy({ it.brandOrSet == NONE_DETECTED }, { it.brandOrSet }, { it.displayName }))
            SORTS[4] -> base.sortedWith(compareBy({ Catalog.rarityRank(it.rarity) }, { it.displayName }))
            SORTS[5] -> base.sortedByDescending { it.mainStat }
            SORTS[6] -> base.sortedWith(compareBy({ Catalog.tags.indexOf(it.tag) }, { it.displayName }))
            SORTS[7] -> base.sortedWith(compareBy({ it.talent.isBlank() }, { it.talent }, { it.displayName }))
            SORTS[8] -> base.sortedByDescending { it.rpm }
            SORTS[9] -> base.sortedByDescending { it.mag }
            else -> base.sortedWith(compareBy({ it.source }, { it.startMs }))
        }
        return if (dupes == DUPES[1]) sorted.filter { seen.add(it.dupKey) } else sorted
    }

    private fun groupKey(i: ScannedItem, mode: String): String = when (mode) {
        GROUPS[1] -> i.category
        GROUPS[2] -> i.slot
        GROUPS[3] -> i.brandOrSet
        GROUPS[4] -> i.rarity
        GROUPS[5] -> i.talentOrNone
        GROUPS[6] -> i.core
        GROUPS[7] -> i.tag
        GROUPS[8] -> i.source.ifBlank { "Unknown video" }
        else -> ""
    }

    private fun groupRank(mode: String, key: String): Int = when (mode) {
        GROUPS[1] -> Catalog.categories.indexOf(key)
        GROUPS[2] -> Catalog.slotRank(key)
        GROUPS[4] -> Catalog.rarityRank(key)
        GROUPS[6] -> Catalog.cores.indexOf(key)
        GROUPS[7] -> Catalog.tags.indexOf(key)
        else -> if (key == UNKNOWN || key == NONE_DETECTED) 999 else 0
    }.let { if (it < 0) 998 else it }

    private fun renderItems() {
        if (!::results.isInitialized || !::spGroup.isInitialized) return
        results.removeAllViews()
        val visible = filtered()
        val counts = items.groupingBy { it.dupKey }.eachCount()
        val reviewed = items.count { it.reviewed }
        summary.text = "Showing ${visible.size} of ${items.size} items • $reviewed reviewed • " +
            "${items.count { it.favorite }} favorites"
        if (items.isEmpty()) {
            results.addView(label("No items yet. Choose a recording and tap Analyze video.", 14f, SUBTLE))
            return
        }
        if (visible.isEmpty()) {
            results.addView(label("No items match the current filters.", 14f, SUBTLE))
            return
        }
        val mode = sel(spGroup)
        if (mode == GROUPS[0]) {
            visible.forEach { addRow(it, counts) }
        } else {
            visible.groupBy { groupKey(it, mode) }.entries
                .sortedWith(compareBy({ groupRank(mode, it.key) }, { it.key }))
                .forEach { (key, list) ->
                    results.addView(label("${key.uppercase(Locale.US)}  (${list.size})", 13f, ACCENT, bold = true)
                        .apply { setPadding(0, dp(12), 0, dp(4)) })
                    list.forEach { addRow(it, counts) }
                }
        }
    }

    private fun rarityColor(r: String) = when (r) {
        "Exotic" -> 0xFFE0603A.toInt()
        "Named" -> 0xFFD9B23C.toInt()
        "Gear Set" -> 0xFF52B66A.toInt()
        "High-End" -> 0xFFF2C14E.toInt()
        "Superior" -> 0xFF9B6BD6.toInt()
        "Specialized" -> 0xFF4A90D9.toInt()
        "Standard" -> 0xFF7FB86A.toInt()
        else -> 0xFF5C656D.toInt()
    }

    private fun addRow(item: ScannedItem, counts: Map<String, Int>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(PANEL) }
        row.addView(View(this).apply { setBackgroundColor(rarityColor(item.rarity)) }, LinearLayout.LayoutParams(dp(5), -1))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val copies = counts[item.dupKey] ?: 1
        body.addView(label((if (item.favorite) "★ " else "") + item.displayName + (if (copies > 1) "   ×$copies" else ""),
            15f, bold = true))
        val line2 = listOf(item.slot, item.rarity, item.brandOrSet).filter { it != UNKNOWN && it != NONE_DETECTED }
        body.addView(label(line2.ifEmpty { listOf("Type not detected") }.joinToString("  •  "), 13f, SUBTLE))
        val line3 = mutableListOf<String>()
        if (item.talent.isNotBlank()) line3.add("Talent: ${item.talent}")
        if (item.core != UNKNOWN) line3.add("Core: ${item.core}")
        if (item.category == "Weapon") {
            if (item.mainStat > 0) line3.add("DMG %,d".format(item.mainStat))
            if (item.rpm > 0) line3.add("RPM ${item.rpm}")
            if (item.mag > 0) line3.add("MAG ${item.mag}")
            val missing = listOfNotNull("DMG".takeIf { item.mainStat == 0L }, "RPM".takeIf { item.rpm == 0 },
                "MAG".takeIf { item.mag == 0 })
            if (missing.isNotEmpty()) line3.add("Missing ${missing.joinToString("/")} (Core Stats not visible in clip)")
        } else if (item.mainStat > 0) line3.add("Stat: %,d".format(item.mainStat))
        if (line3.isNotEmpty()) body.addView(label(line3.joinToString("  •  "), 13f, SUBTLE))
        if (item.attributes.isNotBlank()) body.addView(label(item.attributes.replace("; ", "  •  "), 12f, MUTED))
        val extra = listOf(item.augment.takeIf { it.isNotBlank() }?.let { "Augment: $it" }, item.expertise.ifBlank { null })
            .filterNotNull()
        if (extra.isNotEmpty()) body.addView(label(extra.joinToString("  •  "), 12f, MUTED))
        val reviewTxt = if (item.reviewed) "Reviewed" else "Needs review"
        body.addView(label("${item.tag}  •  $reviewTxt  •  ${item.startSeconds}–${item.endSeconds}s  •  ${item.source}",
            11f, if (item.reviewed) MUTED else WARN))
        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { editItem(item) }
        row.setOnLongClickListener {
            item.favorite = !item.favorite; store.save(items); renderItems(); true
        }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(6))
        results.addView(row, lp)
    }

    // ---------------------------------------------------------------- Editing & bulk actions

    private fun editItem(item: ScannedItem) {
        fun field(hintText: String, value: String) = EditText(this).apply {
            hint = hintText; setText(value); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        fun picker(options: List<String>, value: String) = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(options.indexOf(value).coerceAtLeast(0))
        }
        val name = field("Item name", item.name)
        val category = picker(Catalog.categories + UNKNOWN, item.category)
        val slot = picker(Catalog.allSlots + UNKNOWN, item.slot)
        val rarity = picker(Catalog.rarities + UNKNOWN, item.rarity)
        val core = picker(Catalog.cores + UNKNOWN, item.core)
        val tag = picker(Catalog.tags, item.tag)
        val brand = field("Brand", item.brand)
        val gearSet = field("Gear set", item.gearSet)
        val talent = field("Talent", item.talent)
        val stat = EditText(this).apply {
            hint = "Total damage / main stat"; setText(if (item.mainStat > 0) item.mainStat.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val rpm = EditText(this).apply {
            hint = "RPM"; setText(if (item.rpm > 0) item.rpm.toString() else ""); inputType = InputType.TYPE_CLASS_NUMBER
        }
        val mag = EditText(this).apply {
            hint = "Magazine"; setText(if (item.mag > 0) item.mag.toString() else ""); inputType = InputType.TYPE_CLASS_NUMBER
        }
        val attrs = field("Attributes (Name value%; …)", item.attributes)
        val augment = field("Augment", item.augment)
        val expertise = field("Expertise", item.expertise)
        val mods = field("Mods", item.mods)
        val notes = field("Notes (e.g. recalibrate CHC → CHD)", item.notes)
        val fav = CheckBox(this).apply { text = "Favorite"; isChecked = item.favorite }
        val raw = EditText(this).apply {
            hint = "Recognized text"; setText(item.rawText); minLines = 6; maxLines = 12; textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val redetect = Button(this).apply {
            text = "Re-detect fields from text"; isAllCaps = false
            setOnClickListener {
                val p = ItemParser.parse(raw.text.toString())
                if (name.text.isBlank()) name.setText(p.name)
                category.setSelection((Catalog.categories + UNKNOWN).indexOf(p.category).coerceAtLeast(0))
                slot.setSelection((Catalog.allSlots + UNKNOWN).indexOf(p.slot).coerceAtLeast(0))
                rarity.setSelection((Catalog.rarities + UNKNOWN).indexOf(p.rarity).coerceAtLeast(0))
                core.setSelection((Catalog.cores + UNKNOWN).indexOf(p.core).coerceAtLeast(0))
                brand.setText(p.brand); gearSet.setText(p.gearSet); talent.setText(p.talent)
                stat.setText(if (p.mainStat > 0) p.mainStat.toString() else "")
                rpm.setText(if (p.rpm > 0) p.rpm.toString() else ""); mag.setText(if (p.mag > 0) p.mag.toString() else "")
                attrs.setText(p.attributes); augment.setText(p.augment); expertise.setText(p.expertise); mods.setText(p.mods)
            }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
            addView(name)
            addView(labeledRow("Category", category)); addView(labeledRow("Slot / type", slot))
            addView(labeledRow("Rarity", rarity)); addView(labeledRow("Core attribute", core))
            addView(labeledRow("Tag", tag))
            addView(brand); addView(gearSet); addView(talent); addView(stat); addView(rpm); addView(mag)
            addView(attrs); addView(augment); addView(expertise); addView(mods); addView(notes); addView(fav)
            addView(label("Recognized text", 12f, MUTED).apply { setPadding(0, dp(8), 0, 0) })
            addView(raw); addView(redetect)
        }
        // Labels inside the dialog use the dialog's own text colors.
        for (i in 0 until form.childCount) {
            val c = form.getChildAt(i)
            if (c is LinearLayout && c.childCount > 0) (c.getChildAt(0) as? TextView)?.setTextColor(MUTED)
        }
        AlertDialog.Builder(this)
            .setTitle("Review item")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save") { _, _ ->
                item.name = name.text.toString().trim()
                item.category = category.selectedItem as String
                item.slot = slot.selectedItem as String
                item.rarity = rarity.selectedItem as String
                item.core = core.selectedItem as String
                item.tag = tag.selectedItem as String
                item.brand = brand.text.toString().trim()
                item.gearSet = gearSet.text.toString().trim()
                item.talent = talent.text.toString().trim()
                item.mainStat = stat.text.toString().toLongOrNull() ?: 0L
                item.notes = notes.text.toString().trim()
                item.rpm = rpm.text.toString().toIntOrNull() ?: 0
                item.mag = mag.text.toString().toIntOrNull() ?: 0
                item.attributes = attrs.text.toString().trim()
                item.augment = augment.text.toString().trim()
                item.expertise = expertise.text.toString().trim()
                item.mods = mods.text.toString().trim()
                item.favorite = fav.isChecked
                item.rawText = raw.text.toString()
                item.reviewed = true
                store.save(items); refreshDynamicFilters(); renderItems()
            }
            .setNeutralButton("Delete") { _, _ ->
                items.remove(item); store.save(items); refreshDynamicFilters(); renderItems()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetFilters() {
        etSearch.setText("")
        listOf(spCategory, spSlot, spRarity, spBrand, spTalent, spCore, spTag, spAttr, spDupes, spSort, spGroup)
            .forEach { it.setSelection(0) }
        cbFavorites.isChecked = false; cbNeedsReview.isChecked = false; cbHasTalent.isChecked = false
        renderItems()
    }

    private fun tagShown() {
        val shown = filtered()
        if (shown.isEmpty()) { toast("No items are shown."); return }
        AlertDialog.Builder(this)
            .setTitle("Tag ${shown.size} shown items as…")
            .setItems(Catalog.tags.toTypedArray()) { _, which ->
                shown.forEach { it.tag = Catalog.tags[which] }
                store.save(items); renderItems()
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** Keeps one copy of each repeated item, preferring reviewed copies and the most complete OCR text. */
    private fun removeRepeats() {
        val keep = items.groupBy { it.dupKey }.values.map { copies ->
            copies.maxWithOrNull(compareBy<ScannedItem>({ it.reviewed }, { it.favorite }, { it.rawText.length }))!!
        }.toSet()
        val removed = items.size - keep.size
        if (removed == 0) { toast("No repeated items found."); return }
        AlertDialog.Builder(this)
            .setTitle("Remove $removed repeated items?")
            .setMessage("One copy of each item is kept (reviewed and favorite copies are preferred).")
            .setPositiveButton("Remove") { _, _ ->
                items.retainAll(keep); store.save(items); refreshDynamicFilters(); renderItems()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun clearAll() {
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Clear all ${items.size} items?")
            .setMessage("This removes your saved list from the app. Export a CSV first if you want to keep it.")
            .setPositiveButton("Clear") { _, _ ->
                items.clear(); store.save(items); refreshDynamicFilters(); renderItems()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun exportCsv() {
        if (items.isEmpty()) { toast("Nothing to export yet."); return }
        val shown = filtered()
        fun launch(list: List<ScannedItem>) {
            pendingCsv = ScannedItem.CSV_HEADER + "\n" + list.joinToString("\n") { it.csvRow() }
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "division2_stash.csv")
            }, SAVE_CSV)
        }
        if (shown.size == items.size) { launch(items); return }
        AlertDialog.Builder(this)
            .setTitle("Export CSV")
            .setItems(arrayOf("Shown items only (${shown.size}, current sort)", "All items (${items.size})")) { _, which ->
                launch(if (which == 0) shown else items)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    @Deprecated("Legacy result API retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        if (requestCode == PICK_VIDEO) {
            data?.data?.let { onVideoSelected(it) }
        } else if (requestCode == SAVE_CSV) {
            data?.data?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { out -> OutputStreamWriter(out).use { it.write(pendingCsv) } }
                status.text = "CSV exported. Open it in Excel or Google Sheets."
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

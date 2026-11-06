package com.example.division2stashscanner

import java.util.Locale

/**
 * Screen layout of the Division 2 stash / inventory screen, as fractions of the frame so the same
 * values work for 1080p, 1440p and 4K (16:9). Measured from a 1920×1080 Xbox capture.
 */
object StashLayout {
    // Item list column, used to find the highlighted (orange) row.
    const val LIST_LEFT = 900f / 1920f
    const val LIST_RIGHT = 1400f / 1920f
    const val TOP = 140f / 1080f
    const val BOTTOM = 860f / 1080f
    // Item name inside a highlighted row, relative to the top of the highlight band.
    const val NAME_LEFT = 1060f / 1920f
    const val NAME_RIGHT = 1345f / 1920f
    const val NAME_TOP_OFFSET = 4f / 1080f
    const val NAME_HEIGHT = 46f / 1080f
    // Item details panel on the right.
    const val PANEL_LEFT = 1458f / 1920f
    const val PANEL_RIGHT = 1870f / 1920f
    // Minimum height of a highlight band, and how far it may move before a frame counts as "scrolling".
    const val MIN_BAND = 38f / 1080f
    const val MOVE_TOLERANCE = 6f / 1080f

    /** The game's selection highlight (orange). */
    fun isHighlight(r: Int, g: Int, b: Int) = r > 190 && g in 81..184 && b < 130 && r - b > 100

    /**
     * Finds the highlighted row given, for each pixel row of the list column, the fraction of
     * highlight-coloured pixels. Returns the band's top/bottom row index, or null.
     */
    fun findBand(rowRatios: FloatArray, minBandRows: Int): IntArray? {
        var best: IntArray? = null
        var start = -1
        var lastHit = -10
        for (y in rowRatios.indices) {
            if (rowRatios[y] > 0.45f) {
                if (y - lastHit > 3) start = y
                lastHit = y
                if (best == null || y - start > best[1] - best[0]) best = intArrayOf(start, y)
            }
        }
        return best?.takeIf { it[1] - it[0] >= minBandRows }
    }
}

/** One sampled frame: the highlighted row's name (if any) and the OCR text of the details panel. */
data class FrameRead(val ms: Long, val rowName: String, val bandTop: Int, val moving: Boolean, val panel: String)

/** [panels] holds each usable frame's panel text separately, so fields can be read per frame and voted on. */
data class ItemGroup(val startMs: Long, val endMs: Long, val name: String, val panelText: String,
                     val panels: List<String> = listOf(panelText))

object ScanLogic {
    private fun norm(s: String) = s.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")

    /** Removes ammo-icon / border artefacts that OCR picks up around a row's item name. */
    fun cleanRowName(raw: String): String {
        val line = raw.lines().map { it.trim() }.firstOrNull { it.count(Char::isLetter) >= 2 } ?: return ""
        val tokens = line.split(Regex("\\s+")).toMutableList()
        fun junk(t: String) = t.count(Char::isLetterOrDigit) == 0 ||
            (t.length <= 3 && t.all { it in "iIl1|!:;.,-_=~*'\"" }) ||
            (t.length <= 2 && t.none(Char::isUpperCase) && t.none(Char::isDigit))
        while (tokens.isNotEmpty() && junk(tokens.first())) tokens.removeAt(0)
        while (tokens.isNotEmpty() && (junk(tokens.last()) || tokens.last().equals("level", true))) tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ").trim()
    }

    fun similarity(a: String, b: String): Double {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val d = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) d[i][0] = i
        for (j in 0..y.length) d[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        }
        return 1.0 - d[x.length][y.length].toDouble() / maxOf(x.length, y.length)
    }

    fun sameName(a: String, b: String) = similarity(a, b) >= 0.6

    /**
     * Rebuilds a scrolled panel by merging each frame's lines into one list in reading order,
     * so the item header, stats, attributes and talents seen at different scroll positions are kept.
     */
    fun mergePanels(panels: List<String>): String {
        val merged = mutableListOf<String>()
        val keys = mutableListOf<String>()
        for (panel in panels) {
            var pos = -1
            for (line in panel.lines().map { it.trim() }.filter { it.isNotBlank() }) {
                val k = norm(line)
                if (k.length < 2) continue
                val found = keys.indexOfFirst { it == k || (k.length >= 8 && similarity(it, k) >= 0.85) }
                if (found >= 0) {
                    pos = found
                    if (line.length > merged[found].length && similarity(merged[found], line) >= 0.85) merged[found] = line
                } else {
                    pos += 1
                    merged.add(pos, line); keys.add(pos, k)
                }
            }
        }
        return merged.joinToString("\n")
    }

    /**
     * Groups frames into items using the highlighted row's name. Frames taken while the list is
     * scrolling are ignored, single-frame OCR misreads are absorbed, and the first frame of each
     * item is not used for its details because the panel can lag behind the selection.
     */
    fun groupByRow(frames: List<FrameRead>, stepMs: Long, holdMs: Long): List<ItemGroup> {
        val usable = frames.filter { it.rowName.isNotBlank() && !it.moving }
        val out = mutableListOf<ItemGroup>()
        var cur = mutableListOf<FrameRead>()
        val gapMs = maxOf(2500L, stepMs * 3)
        fun groupName(g: List<FrameRead>): String {
            // Most common reading; ties go to the longest reading.
            return g.groupingBy { it.rowName }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
                .first().key
        }
        var prevWords: Set<String> = emptySet()
        fun flush() {
            if (cur.isEmpty()) return
            val start = cur.first().ms; val end = cur.last().ms
            val name = groupName(cur)
            // The panel can lag behind the selection: drop the first frames if they still show the previous item.
            val detail = cur.filterIndexed { idx, f ->
                idx >= 2 || prevWords.isEmpty() || mentions(f.panel, name) || containment(words(f.panel), prevWords) < 0.85
            }.ifEmpty { cur }
            if (end - start + stepMs >= holdMs) {
                out.add(ItemGroup(start, end, name, mergePanels(detail.map { it.panel }), detail.map { it.panel }))
            }
            prevWords = cur.flatMap { words(it.panel) }.toSet()
            cur = mutableListOf()
        }
        for ((i, f) in usable.withIndex()) {
            if (cur.isEmpty()) { cur.add(f); continue }
            val name = groupName(cur)
            if (sameName(name, f.rowName) && f.ms - cur.last().ms <= gapMs) { cur.add(f); continue }
            // A one-frame misread: the next frame matches the current item again.
            val next = usable.getOrNull(i + 1)
            if (next != null && sameName(name, next.rowName) && !sameName(f.rowName, next.rowName)) continue
            flush(); cur.add(f)
        }
        flush()
        return mergeRepeatVisits(out)
    }

    private fun words(s: String) = s.lowercase(Locale.US).split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
    private fun containment(a: Set<String>, b: Set<String>) = if (a.isEmpty()) 1.0 else a.count { it in b }.toDouble() / a.size
    private fun mentions(panel: String, name: String) = name.isNotBlank() && panel.lines().any { similarity(it, name) >= 0.8 }

    /** If the user returns to the same item straight away, the two visits become one item. */
    private fun mergeRepeatVisits(groups: List<ItemGroup>): List<ItemGroup> {
        val out = mutableListOf<ItemGroup>()
        for (g in groups) {
            val last = out.lastOrNull()
            if (last != null && sameName(last.name, g.name)) {
                out[out.size - 1] = ItemGroup(last.startMs, g.endMs, last.name,
                    mergePanels(listOf(last.panelText, g.panelText)), last.panels + g.panels)
            } else out.add(g)
        }
        return out
    }

    /** Fallback when no highlighted row is visible (e.g. other menus): group by panel text similarity. */
    fun groupByPanel(frames: List<FrameRead>, stepMs: Long, holdMs: Long): List<ItemGroup> {
        val usable = frames.filter { it.panel.isNotBlank() }
        if (usable.isEmpty()) return emptyList()
        val out = mutableListOf<ItemGroup>()
        var cur = mutableListOf(usable.first())
        fun words(s: String) = s.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        fun flush() {
            if (cur.last().ms - cur.first().ms + stepMs >= holdMs) {
                val text = mergePanels(cur.map { it.panel })
                out.add(ItemGroup(cur.first().ms, cur.last().ms, "", text, cur.map { it.panel }))
            }
        }
        for (f in usable.drop(1)) {
            val a = words(cur.last().panel); val b = words(f.panel)
            val sim = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
            if (sim >= 0.45 && f.ms - cur.last().ms <= maxOf(2000L, stepMs * 3)) cur.add(f)
            else { flush(); cur = mutableListOf(f) }
        }
        flush()
        return out
    }

    fun group(frames: List<FrameRead>, stepMs: Long, holdMs: Long): List<ItemGroup> {
        val withRow = frames.count { it.rowName.isNotBlank() }
        return if (withRow >= frames.size / 2) groupByRow(frames, stepMs, holdMs) else groupByPanel(frames, stepMs, holdMs)
    }
}

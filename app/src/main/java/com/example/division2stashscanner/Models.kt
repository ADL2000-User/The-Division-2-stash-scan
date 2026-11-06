package com.example.division2stashscanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

const val UNKNOWN = "Unknown"
const val NONE_DETECTED = "None detected"

/** Division 2 reference vocabulary used to classify OCR text. */
object Catalog {
    val categories = listOf("Weapon", "Gear")
    val gearSlots = listOf("Mask", "Backpack", "Chest", "Gloves", "Holster", "Kneepads")
    val weaponTypes = listOf("Assault Rifle", "Rifle", "Marksman Rifle", "SMG", "LMG", "Shotgun", "Pistol")
    val allSlots = gearSlots + weaponTypes
    val rarities = listOf("Exotic", "Named", "Gear Set", "Prototype", "High-End", "Superior", "Specialized", "Standard")
    val cores = listOf("Weapon Damage", "Armor", "Skill Tier")
    val tags = listOf("Unsorted", "Keep", "Build piece", "Recalibrate", "Sell / Deconstruct")

    /** Ordered so longer phrases win over shorter ones (e.g. "assault rifle" before "rifle"). */
    val slotAliases = listOf(
        "assault rifle" to "Assault Rifle",
        "marksman rifle" to "Marksman Rifle",
        "light machinegun" to "LMG", "light machine gun" to "LMG", "machinegun" to "LMG",
        "sub machine gun" to "SMG", "submachine gun" to "SMG", "submachinegun" to "SMG",
        "body armor" to "Chest", "body armour" to "Chest", "chest" to "Chest", "vest" to "Chest",
        "knee pads" to "Kneepads", "kneepads" to "Kneepads", "kneepad" to "Kneepads",
        "backpack" to "Backpack", "mask" to "Mask", "gloves" to "Gloves", "holster" to "Holster",
        "smg" to "SMG", "lmg" to "LMG", "shotgun" to "Shotgun", "pistol" to "Pistol", "sidearm" to "Pistol",
        "rifle" to "Rifle"
    )

    /** OCR key (normalized, lowercase) -> display name. */
    val brands = listOf(
        "5 11" to "5.11 Tactical", "airaldi" to "Airaldi Holdings", "alps summit" to "Alps Summit Armament",
        "badger tuff" to "Badger Tuff", "belstone" to "Belstone Armory", "brazos" to "Brazos de Arcabuco",
        "china light" to "China Light Industries", "ceska" to "Ceska Vyroba", "douglas" to "Douglas & Harding",
        "electrique" to "Electrique", "empress" to "Empress International", "fenris" to "Fenris Group",
        "gila guard" to "Gila Guard", "golan" to "Golan Gear", "grupo sombra" to "Grupo Sombra",
        "habsburg" to "Habsburg Guard", "hana u" to "Hana-U Corporation", "legatus" to "Legatus",
        "lengmo" to "Lengmo", "murakami" to "Murakami Industries", "overlord" to "Overlord Armaments",
        "palisade" to "Palisade Steelworks", "petrov" to "Petrov Defense Group", "providence" to "Providence Defense",
        "richter" to "Richter & Kaiser", "sokolov" to "Sokolov Concern", "uzina" to "Uzina Getica",
        "walker harris" to "Walker, Harris & Co.", "wyvern" to "Wyvern Wear", "yaahl" to "Yaahl Gear",
        "zwiadowka" to "Zwiadowka"
    )

    val gearSets = listOf(
        "aces & eights" to "Aces & Eights", "aces and eights" to "Aces & Eights", "aegis" to "Aegis",
        "eclipse protocol" to "Eclipse Protocol", "foundry bulwark" to "Foundry Bulwark",
        "future initiative" to "Future Initiative", "hard wired" to "Hard Wired", "hardwired" to "Hard Wired",
        "heartbreaker" to "Heartbreaker", "hotshot" to "Hotshot", "hunter s fury" to "Hunter's Fury",
        "hunters fury" to "Hunter's Fury", "negotiator s dilemma" to "Negotiator's Dilemma",
        "negotiators dilemma" to "Negotiator's Dilemma", "ongoing directive" to "Ongoing Directive",
        "rigger" to "Rigger", "striker s battlegear" to "Striker's Battlegear",
        "strikers battlegear" to "Striker's Battlegear", "system corruption" to "System Corruption",
        "tip of the spear" to "Tip of the Spear", "true patriot" to "True Patriot",
        "umbra initiative" to "Umbra Initiative", "virtuoso" to "Virtuoso"
    )

    val talents = listOf(
        // Exotic weapon talents seen in stash panels
        "payment in kind", "breathe free", "big game hunter", "rule them all", "perturb", "adaptive instincts",
        "disruptor rounds", "plague of the outcasts", "cover shooter", "perfect precision strike", "eyeless",
        // Gear talents (chest / backpack)
        "obliterate", "glass cannon", "unbreakable", "vigilance", "spotter", "intimidate", "headhunter",
        "kinetic momentum", "tamper proof", "bloodsucker", "clutch", "composure", "concussion", "galvanize",
        "leadership", "overwatch", "shock and awe", "combined arms", "wicked", "braced", "protected reload",
        "unstoppable force", "vanguard", "companion", "creeping death", "efficient", "energize", "overclock",
        "opportunistic", "patience", "safeguard", "skilled", "adrenaline rush", "calculated",
        // Weapon talents
        "killer", "optimist", "ignited", "in sync", "strained", "ranger", "steady handed", "breadbasket",
        "close & personal", "close and personal", "fast hands", "frenzy", "lucky shot", "measured", "naked",
        "near sighted", "on empty", "overflowing", "preservation", "pummel", "reformation", "rifleman",
        "sadist", "spike", "streamline", "unhinged", "vindictive", "eyeless", "boomerang", "determined",
        "first blood", "flatline", "future perfect", "outsider"
    )

    private val uiNoise = setOf(
        "inventory", "stash", "equip", "equipped", "compare", "mark as junk", "favorite", "deconstruct",
        "back", "select", "details", "inspect", "close", "loadout", "gear", "weapons", "mods", "attributes",
        "talent", "talents", "brand set", "gear set", "high end", "high-end", "exotic", "named"
    )

    /** Attribute names shown on weapons and gear (OCR text is matched against these). */
    val attributes = listOf(
        "Critical Hit Chance", "Critical Hit Damage", "Headshot Damage", "Damage to Armor", "Health Damage",
        "Damage to Target out of Cover", "Rate of Fire", "Accuracy", "Stability", "Reload Speed", "Optimal Range",
        "Magazine Size", "Swap Speed", "Assault Rifle Damage", "Marksman Rifle Damage", "Rifle Damage", "LMG Damage",
        "SMG Damage", "Shotgun Damage", "Pistol Damage", "Weapon Damage", "Armor Regeneration", "Armor on Kill",
        "Hazard Protection", "Explosive Resistance", "Skill Haste", "Skill Damage", "Repair Skills",
        "Status Effects", "Health", "Armor", "Skill Tier"
    )

    /** Maps an OCR attribute line such as "+15.5% Critical Hit Chance" to its canonical name. */
    fun matchAttribute(line: String): String? {
        val n = " " + line.lowercase(Locale.US).replace("dmg", "damage").replace(Regex("[^a-z ]+"), " ")
            .replace(Regex("\\s+"), " ").trim() + " "
        return attributes.firstOrNull { n.contains(" " + it.lowercase(Locale.US) + " ") }
    }

    fun isUiNoise(line: String) = line.lowercase(Locale.US).trim() in uiNoise

    /** Exotic weapons whose rarity label may be scrolled out of view in the details panel. */
    /** Exotic weapons and their types; used when the rarity/type lines are scrolled out of view. */
    val exoticWeapons = mapOf(
        "big alejandro" to "LMG", "bluescreen" to "LMG", "bullet king" to "LMG", "pestilence" to "LMG",
        "agitator" to "Assault Rifle", "chameleon" to "Assault Rifle", "strega" to "Assault Rifle",
        "the bighorn" to "Assault Rifle", "eagle bearer" to "Assault Rifle", "st elmo's engine" to "Assault Rifle",
        "capacitor" to "Assault Rifle", "backfire" to "SMG", "lady death" to "SMG", "ouroboros" to "SMG",
        "the chatterbox" to "SMG", "oxpecker" to "SMG", "scorpio" to "Shotgun", "sweet dreams" to "Shotgun",
        "liberty" to "Pistol", "regulus" to "Pistol", "nemesis" to "Marksman Rifle", "the ravenous" to "Rifle"
    )
    fun isKnownExotic(name: String) = exoticWeapons.containsKey(name.lowercase(Locale.US).trim())
    fun exoticType(name: String) = exoticWeapons[name.lowercase(Locale.US).trim()]

    /** Weapon model names that contain digits, which OCR tends to misread (GR9 -> GRS). */
    val modelNames = listOf("GR9", "M60", "M249", "RPK-74", "MG5", "L86", "M4", "AK-M", "P416", "G36", "FAMAS 2010",
        "AUG A3", "MP5", "MP7", "UMP-45", "T821", "MPX", "P90", "MK17", "M1A", "SRS", "G28", "M700", "SVD", "1886",
        "M870", "SPAS-12", "SASG-12", "M1911", "93R", "X-45", "D50", "PF45", "SMG-9", "PP-19", "CTAR-21", "F2000",
        "ACR", "SIG 556", "SG 553", "AK-74", "M16A2", "M44", "SR-1", "G2", "KSG", "M32A1", "ACS-12")
    private fun confusable(s: String) = s.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        .map { when (it) { '5', '9', '$' -> 'S'; '0', 'D', 'Q' -> 'O'; '1', 'I', 'L' -> 'I'; '8' -> 'B'; '2' -> 'Z'; '6' -> 'G'; else -> it } }
        .joinToString("")
    /** Replaces a misread model name with the catalog spelling when they match after OCR-confusable folding. */
    fun fixModelName(name: String): String {
        val words = name.trim().split(Regex("\\s+"))
        return words.joinToString(" ") { w ->
            if (w.none(Char::isDigit) && w.length > 4) w
            else modelNames.firstOrNull { it.count(Char::isDigit) > 0 && confusable(it) == confusable(w) && it != w } ?: w
        }.let { if (it == name.trim()) name else it }
    }

    fun rarityRank(r: String) = rarities.indexOf(r).let { if (it < 0) 99 else it }
    fun slotRank(s: String) = allSlots.indexOf(s).let { if (it < 0) 99 else it }
}

class ScannedItem(
    var id: Long,
    var source: String,
    var startMs: Long,
    var endMs: Long,
    var rawText: String,
    var name: String = "",
    var category: String = UNKNOWN,
    var slot: String = UNKNOWN,
    var rarity: String = UNKNOWN,
    var brand: String = "",
    var gearSet: String = "",
    var talent: String = "",
    var core: String = UNKNOWN,
    var mainStat: Long = 0,
    var tag: String = "Unsorted",
    var favorite: Boolean = false,
    var reviewed: Boolean = false,
    var notes: String = "",
    var rpm: Int = 0,
    var mag: Int = 0,
    var attributes: String = "",
    var mods: String = "",
    var augment: String = "",
    var expertise: String = ""
) {
    val startSeconds get() = (startMs / 1000L).toInt()
    val endSeconds get() = (endMs / 1000L).toInt()
    val displayName get() = name.ifBlank { "Unidentified item" }
    val brandOrSet get() = gearSet.ifBlank { brand }.ifBlank { NONE_DETECTED }
    val talentOrNone get() = talent.ifBlank { NONE_DETECTED }
    val dupKey get() = listOf(name.lowercase(Locale.US).trim(), slot, brandOrSet, talent).joinToString("|")
    /** Attribute names only, e.g. ["Critical Hit Chance", "Health Damage"]. */
    val attributeNames get() = attributes.split(";").map { it.trim().substringBeforeLast(" ").trim() }
        .filter { it.isNotBlank() }

    fun csvRow(): String = listOf(
        source, startSeconds.toString(), endSeconds.toString(), displayName, category, slot, rarity,
        brand, gearSet, talent, core, if (mainStat > 0) mainStat.toString() else "",
        if (rpm > 0) rpm.toString() else "", if (mag > 0) mag.toString() else "", attributes, augment, expertise,
        mods, tag, if (favorite) "yes" else "", if (reviewed) "yes" else "", notes, rawText.replace("\n", " | ")
    ).joinToString(",") { csvEscape(it) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("source", source); put("startMs", startMs); put("endMs", endMs)
        put("rawText", rawText); put("name", name); put("category", category); put("slot", slot)
        put("rarity", rarity); put("brand", brand); put("gearSet", gearSet); put("talent", talent)
        put("core", core); put("mainStat", mainStat); put("tag", tag); put("favorite", favorite)
        put("reviewed", reviewed); put("notes", notes); put("rpm", rpm); put("mag", mag)
        put("attributes", attributes); put("mods", mods); put("augment", augment); put("expertise", expertise)
    }

    companion object {
        const val CSV_HEADER = "Source video,Start (s),End (s),Item name,Category,Slot / type,Rarity,Brand," +
            "Gear set,Talent,Core attribute,Total damage / main stat,RPM,Magazine,Attributes,Augment,Expertise," +
            "Mods,Tag,Favorite,Reviewed,Notes,All recognized text"

        fun fromJson(o: JSONObject) = ScannedItem(
            id = o.optLong("id"), source = o.optString("source"), startMs = o.optLong("startMs"),
            endMs = o.optLong("endMs"), rawText = o.optString("rawText"), name = o.optString("name"),
            category = o.optString("category", UNKNOWN), slot = o.optString("slot", UNKNOWN),
            rarity = o.optString("rarity", UNKNOWN), brand = o.optString("brand"),
            gearSet = o.optString("gearSet"), talent = o.optString("talent"),
            core = o.optString("core", UNKNOWN), mainStat = o.optLong("mainStat"),
            tag = o.optString("tag", "Unsorted"), favorite = o.optBoolean("favorite"),
            reviewed = o.optBoolean("reviewed"), notes = o.optString("notes"),
            rpm = o.optInt("rpm"), mag = o.optInt("mag"), attributes = o.optString("attributes"),
            mods = o.optString("mods"), augment = o.optString("augment"), expertise = o.optString("expertise")
        )
    }
}

fun csvEscape(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

/** Result of reading one item panel's OCR text. */
data class ParsedFields(
    val name: String, val category: String, val slot: String, val rarity: String, val brand: String,
    val gearSet: String, val talent: String, val core: String, val mainStat: Long,
    val rpm: Int = 0, val mag: Int = 0, val attributes: String = "", val mods: String = "",
    val augment: String = "", val expertise: String = ""
)

object ItemParser {
    private fun norm(s: String) = " " + s.lowercase(Locale.US).replace(Regex("[^a-z0-9&]+"), " ").trim() + " "
    private fun hasKey(normText: String, key: String) = normText.contains(" $key ")

    /** Section headers of the item details panel, in the order the game shows them. */
    private val sections = listOf(
        "core stats", "augment", "expertise", "core attributes", "core attribute", "attributes", "attribute",
        "weapon handling", "talents", "talent", "mods", "brand set", "gear set bonus", "equipped items"
    )

    private fun sectionOf(line: String): String? {
        val n = norm(line).trim()
        return sections.firstOrNull { n == it || (n.startsWith("$it ") && n.length <= it.length + 4) }
            ?.removeSuffix("s")?.let { if (it == "core attribute") "core attributes" else it }
    }

    /** "116.81k" -> 116810, "185,000" -> 185000 */
    fun parseNumber(tok: String): Long? {
        var t = tok.trim().lowercase(Locale.US)
        t = if (Regex("^[0-9]+,[0-9]{1,2}[km]$").matches(t)) t.replace(',', '.') else t.replace(",", "")
        val m = Regex("^([0-9]+(?:\\.[0-9]+)?)([km]?)$").find(t) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2]) { "k" -> (v * 1_000).toLong(); "m" -> (v * 1_000_000).toLong(); else -> v.toLong() }
    }

    private fun cleanName(line: String): String {
        val words = line.trim().split(Regex("\\s+")).toMutableList()
        while (words.size > 1 && words.first().none(Char::isLetterOrDigit)) words.removeAt(0)
        while (words.size > 1 && (words.last().length <= 2 || words.last().any { !it.isLetter() && it != '\'' && it != '-' }))
            words.removeAt(words.size - 1)
        return words.joinToString(" ")
    }

    fun parse(raw: String, knownName: String = ""): ParsedFields {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val all = norm(raw)

        // Split the panel into sections ("" = header block above the first section).
        val bySection = linkedMapOf<String, MutableList<String>>()
        var current = ""
        var seenCoreStats = false
        for (l in lines) {
            val sec = sectionOf(l)
            // "Augment Level" also appears in the header of Prototype items; only treat it as a section later on.
            if (sec != null && (sec != "augment" || seenCoreStats)) {
                current = sec; if (sec == "core stat") seenCoreStats = true
                bySection.getOrPut(current) { mutableListOf() }
                continue
            }
            bySection.getOrPut(current) { mutableListOf() }.add(l)
        }
        val header = bySection[""].orEmpty()
        val nonEquipped = lines.filterNot { it in bySection["equipped item"].orEmpty() }

        // Slot / weapon type: ignore attribute lines such as "+15% Assault Rifle Damage".
        val labelLines = nonEquipped.filter { l -> !l.contains('%') && !l.contains('+') && !l.lowercase(Locale.US).contains("damage") }
        val labelText = norm(labelLines.joinToString(" "))
        val slot = Catalog.slotAliases.firstOrNull { hasKey(labelText, it.first) }?.second ?: UNKNOWN
        val category = when (slot) {
            in Catalog.gearSlots -> "Gear"
            in Catalog.weaponTypes -> "Weapon"
            else -> UNKNOWN
        }

        val gearSet = Catalog.gearSets.firstOrNull { hasKey(all, it.first) }?.second ?: ""
        val brand = if (gearSet.isNotEmpty()) "" else Catalog.brands.firstOrNull { hasKey(all, it.first) }?.second ?: ""

        // Talent: the first line under the "Talents" header; otherwise a known talent name on its own line.
        val talentSection = bySection["talent"].orEmpty()
        val talent = talentSection.firstOrNull { it.count(Char::isLetter) >= 3 }?.let { cleanName(it) }
            ?.let { t -> Catalog.talents.firstOrNull { norm(t).trim().startsWith("$it ") }?.let { titleCase(it) }
                ?: t.replace(Regex("(\\s+[A-Z]{1,3})+$"), "") }
            ?: lines.firstNotNullOfOrNull { line ->
                val n = norm(line).trim()
                if (n.length > 34) null
                else Catalog.talents.firstOrNull { n == it || n.startsWith("$it ") }?.let { titleCase(it) }
            } ?: ""

        val headerNorm = norm(header.joinToString(" "))
        val rarity = when {
            hasKey(headerNorm, "exotic") || hasKey(all, "only 1 exotic") -> "Exotic"
            gearSet.isNotEmpty() || hasKey(headerNorm, "gear set") -> "Gear Set"
            hasKey(all, "prototype") -> "Prototype"
            hasKey(headerNorm, "named") -> "Named"
            hasKey(all, "high end") || brand.isNotEmpty() -> "High-End"
            hasKey(headerNorm, "superior") -> "Superior"
            hasKey(headerNorm, "specialized") -> "Specialized"
            hasKey(headerNorm, "standard") -> "Standard"
            hasKey(all, "exotic") -> "Exotic"
            else -> UNKNOWN
        }

        // Core Stats: the first line after the "Total DMG RPM MAG" labels holds the three values.
        var mainStat = 0L; var rpm = 0; var mag = 0
        val coreStats = bySection["core stat"].orEmpty()
        val labelIdx = coreStats.indexOfFirst { norm(it).contains(" total ") || norm(it).contains(" rpm ") }
        if (labelIdx >= 0) {
            val valueLine = coreStats.drop(labelIdx + 1).firstOrNull { l ->
                // A real value line starts with Total DMG (a number of 1000+, e.g. "57.66k"); lines where the DMG was
                // misread ("37 00 = 200", "20 200") would otherwise shift the RPM/MAG values.
                val n = statNumbers(l)
                n.size >= 2 && n.first() >= 1000 && !hasKey(norm(l), "rank") && !hasKey(norm(l), "progress")
            }
            if (valueLine != null) {
                val nums = statNumbers(valueLine)
                mainStat = nums.first()
                // RPM can exceed 1000 (e.g. 1100), so only the first number is the damage value.
                val rest = nums.drop(1)
                rpm = rest.getOrNull(0)?.toInt()?.takeIf { it in 30..2000 } ?: 0
                mag = rest.getOrNull(1)?.toInt()?.takeIf { it in 1..300 } ?: 0
            }
        }
        // Fallback for panels without a readable stat line (gear): the largest plain number, ignoring progress
        // counters such as "82544/88000". Weapons keep 0 instead of guessing.
        val weaponLike = listOf("rpm", "weapon handling", "proficiency", "rank progress", "reload time", "optimal range")
            .any { hasKey(norm(raw), it) }
        if (mainStat == 0L && coreStats.isEmpty() && !weaponLike) {
            mainStat = Regex("\\b[0-9]{1,3}(?:[.][0-9]{1,2})?k\\b|\\b[0-9]{1,3}(?:,[0-9]{3})+\\b|\\b[0-9]{4,7}\\b")
                .findAll(raw.lowercase(Locale.US).replace(Regex("[0-9.,]+\\s*/\\s*\\S*[0-9.,]+"), " ")).mapNotNull { parseNumber(it.value) }.maxOrNull() ?: 0L
        }

        // Attributes from the Core Attributes and Attributes sections: "Name value%".
        // Attribute lines look like "+11.6% Assault Rifle Damage". When the section header is misread they end up in
        // another section, so such lines are also taken from anywhere except mods/talents. Expertise lines
        // ("30 +30.0% Weapon Damage") start with a rank number and are not matched.
        // Stray lines need a one-decimal roll ("11.6%") and cannot be the expertise "Weapon Damage" bonus.
        val attrPattern = Regex("^[^A-Za-z0-9+]*\\+?[0-9]+[.,][0-9]\\s*%\\s+[A-Za-z]")
        val stray = bySection.filterKeys { it !in setOf("mod", "talent", "core attributes", "attribute", "expertise") }
            .values.flatten().filter { attrPattern.containsMatchIn(it) && !hasKey(norm(it), "weapon damage") }
        val attrLines = bySection["core attributes"].orEmpty() + bySection["attribute"].orEmpty() + stray
        val attributes = attrLines.mapNotNull { l ->
            val name = Catalog.matchAttribute(l) ?: return@mapNotNull null
            val value = Regex("([0-9]+(?:[.,][0-9])?)\\s*%").find(l)?.groupValues?.get(1)?.replace(',', '.')
                ?.let { fixPercent(it) }
            name to value
        }.groupBy({ it.first }, { it.second })
            .map { (name, values) -> values.firstOrNull { it != null }?.let { "$name $it%" } ?: name }
            .joinToString("; ")

        val core = when {
            category == "Weapon" -> UNKNOWN
            bySection["core attributes"].orEmpty().any { norm(it).contains(" skill tier ") } || hasKey(all, "skill tier") -> "Skill Tier"
            bySection["core attributes"].orEmpty().any { norm(it).contains(" weapon damage ") } ||
                Regex("(?i)\\b(1[0-5]|[0-9])([.,][0-9])?\\s*%\\s*weapon damage").containsMatchIn(raw) -> "Weapon Damage"
            bySection["core attributes"].orEmpty().any { norm(it).contains(" armor ") } ||
                Regex("(?i)\\b[0-9]{1,3}[,.][0-9]{3}\\s*armou?r\\b").containsMatchIn(raw) -> "Armor"
            else -> UNKNOWN
        }

        val augment = bySection["augment"].orEmpty().firstOrNull { it.count(Char::isLetter) >= 3 && !it.contains('%') }
            ?.let { cleanName(it) } ?: ""
        val expertiseLines = bySection["expertise"].orEmpty()
        val expertise = expertiseLines.firstNotNullOfOrNull { l ->
            Regex("(?i)proficiency rank\\s*([0-9]+)").find(l)?.let { "Proficiency Rank ${it.groupValues[1]}" }
                ?: Regex("^\\D{0,3}([0-9]{1,2})\\s*\\+[0-9]").find(l)?.let { "Expertise ${it.groupValues[1]}" }
                ?: if (norm(l).trim() == "proficient") "Proficient" else null
        } ?: ""
        val modWords = listOf("rail", "slot", "muzzle", "underbarrel", "magazine", "optic")
        val modLines = bySection["mod"].orEmpty()
        val mods = modLines.mapIndexedNotNull { i, l ->
            if (modWords.none { norm(l).contains(" $it ") }) return@mapIndexedNotNull null
            modLines.getOrNull(i + 1)?.takeIf { n ->
                !n.contains('%') && !n.contains('+') && n.count(Char::isLetter) >= 3 && modWords.none { norm(n).contains(" $it ") } &&
                    !norm(n).contains(" empty ")
            }?.let { cleanName(it) }
        }.distinct().take(5).joinToString("; ")

        val name = knownName.ifBlank {
            header.firstOrNull { l ->
                l.length in 3..55 && l.count(Char::isLetter) >= 3 && !l.contains('%') && !Catalog.isUiNoise(l) &&
                    sectionOf(l) == null && !norm(l).contains(" level ") && Catalog.allSlots.none { it.equals(l, true) }
            } ?: lines.firstOrNull { l ->
                l.length in 3..55 && l.count(Char::isLetter) >= 3 && !l.contains('%') && !Catalog.isUiNoise(l) &&
                    Catalog.allSlots.none { it.equals(l, ignoreCase = true) }
            } ?: ""
        }

        return ParsedFields(name, category, slot, rarity, brand, gearSet, talent, core, mainStat,
            rpm, mag, attributes, mods, augment, expertise)
    }

    /**
     * OCR often reads "+" as "1" or "4" and drops decimal points. Weapon and gear attribute rolls are
     * below 60%, so larger values are corrected ("119.8" -> "19.8", "125" -> "12.5").
     */
    fun fixPercent(v: String): String {
        val d = v.toDoubleOrNull() ?: return v
        if (d <= 60) return v
        val dropped = v.drop(1)
        if ((v.startsWith("1") || v.startsWith("4")) && (dropped.toDoubleOrNull() ?: 99.0) <= 60 && dropped.isNotEmpty() && !dropped.startsWith(".")) return dropped
        if (!v.contains('.') && v.length == 3) return v.substring(0, 2) + "." + v.substring(2)
        return v
    }

    private fun attrName(entry: String) = entry.removeSuffix("%").trimEnd { c -> c.isDigit() || c == '.' }.trim()

    /** Numbers on a Core Stats line, skipping progress counters like "80694/88000". */
    private fun statNumbers(line: String) = line.split(Regex("\\s+")).filterNot { it.contains('/') }
        .mapNotNull { parseNumber(it.trim('$', '}', '|', '\'', '[', ']')) }

    /** Parses every frame of an item separately and keeps the most common value for each field. */
    fun parseFrames(panels: List<String>, knownName: String = ""): ParsedFields {
        val parsed = panels.filter { it.isNotBlank() }.map { parse(it, knownName) }
        if (parsed.isEmpty()) return parse("", knownName)
        fun <T> vote(get: (ParsedFields) -> T, empty: (T) -> Boolean): T {
            val vals = parsed.map(get).filterNot(empty)
            return vals.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: get(parsed.first())
        }
        // Attributes / mods: union across frames, keeping the most common value for each attribute name.
        val attrs = parsed.flatMap { it.attributes.split("; ").filter(String::isNotBlank) }
            .groupBy { attrName(it) }
            .map { (_, v) -> v.filter { it.endsWith("%") }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: v.first() }
            .joinToString("; ")
        val mods = parsed.flatMap { it.mods.split("; ").filter(String::isNotBlank) }.distinct().take(5).joinToString("; ")
        val name = Catalog.fixModelName(knownName.ifBlank { vote({ it.name }) { it.isBlank() } })
        // Fallback: infer the weapon type from the core weapon-damage attribute ("Assault Rifle Damage").
        val slot = vote({ it.slot }) { it == UNKNOWN }.let { s ->
            if (s != UNKNOWN) s else {
                // Use parsed attributes plus any "+x% ... Damage" line, since the section header is often misread.
                val a = norm(attrs) + norm(panels.flatMap { it.lines() }.filter { it.contains('%') }.joinToString(" "))
                Catalog.exoticType(name) ?: listOf("assault rifle damage" to "Assault Rifle", "marksman rifle damage" to "Marksman Rifle",
                    "smg damage" to "SMG", "lmg damage" to "LMG", "shotgun damage" to "Shotgun",
                    "pistol damage" to "Pistol", "rifle damage" to "Rifle")
                    .firstOrNull { hasKey(a, it.first) }?.second ?: UNKNOWN
            }
        }
        val votedRarity = if (parsed.any { it.rarity == "Exotic" }) "Exotic" else vote({ it.rarity }) { it == UNKNOWN }
        val rarity = if (votedRarity == UNKNOWN && Catalog.isKnownExotic(name)) "Exotic" else votedRarity
        return ParsedFields(
            name = name,
            category = when (slot) { in Catalog.gearSlots -> "Gear"; in Catalog.weaponTypes -> "Weapon"; else -> UNKNOWN },
            slot = slot,
            rarity = rarity,
            brand = vote({ it.brand }) { it.isBlank() },
            gearSet = vote({ it.gearSet }) { it.isBlank() },
            talent = vote({ it.talent }) { it.isBlank() },
            core = vote({ it.core }) { it == UNKNOWN },
            mainStat = vote({ it.mainStat }) { it == 0L },
            rpm = vote({ it.rpm }) { it == 0 },
            mag = vote({ it.mag }) { it == 0 },
            attributes = attrs, mods = mods,
            augment = vote({ it.augment }) { it.isBlank() },
            expertise = vote({ it.expertise }) { it.isBlank() }
        )
    }

    fun apply(item: ScannedItem, keepName: Boolean = false, panels: List<String>? = null) {
        val known = if (keepName) item.name else ""
        val p = if (panels != null) parseFrames(panels, known) else parse(item.rawText, known)
        if (!keepName || item.name.isBlank()) item.name = p.name
        item.name = Catalog.fixModelName(item.name)
        item.category = p.category; item.slot = p.slot; item.rarity = p.rarity; item.brand = p.brand
        item.gearSet = p.gearSet; item.talent = p.talent; item.core = p.core; item.mainStat = p.mainStat
        item.rpm = p.rpm; item.mag = p.mag; item.attributes = p.attributes; item.mods = p.mods
        item.augment = p.augment; item.expertise = p.expertise
    }

    private fun titleCase(s: String) = s.split(" ").joinToString(" ") { w ->
        if (w == "&" || w == "and" || w == "in" || w == "on") w else w.replaceFirstChar { it.uppercase() }
    }
}

/** Saves the organized list between sessions so a large stash can be built up over several recordings. */
class ItemStore(context: Context) {
    private val file = File(context.filesDir, "stash_items.json")

    fun load(): MutableList<ScannedItem> = try {
        if (!file.exists()) mutableListOf() else {
            val arr = JSONArray(file.readText())
            MutableList(arr.length()) { ScannedItem.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) { mutableListOf() }

    fun save(items: List<ScannedItem>) {
        val arr = JSONArray(); items.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}

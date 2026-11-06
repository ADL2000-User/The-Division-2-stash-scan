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
    val rarities = listOf("Exotic", "Named", "Gear Set", "High-End", "Superior", "Specialized", "Standard")
    val cores = listOf("Weapon Damage", "Armor", "Skill Tier")
    val tags = listOf("Unsorted", "Keep", "Build piece", "Recalibrate", "Sell / Deconstruct")

    /** Ordered so longer phrases win over shorter ones (e.g. "assault rifle" before "rifle"). */
    val slotAliases = listOf(
        "assault rifle" to "Assault Rifle",
        "marksman rifle" to "Marksman Rifle",
        "submachine gun" to "SMG",
        "light machine gun" to "LMG",
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

    fun isUiNoise(line: String) = line.lowercase(Locale.US).trim() in uiNoise

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
    var notes: String = ""
) {
    val startSeconds get() = (startMs / 1000L).toInt()
    val endSeconds get() = (endMs / 1000L).toInt()
    val displayName get() = name.ifBlank { "Unidentified item" }
    val brandOrSet get() = gearSet.ifBlank { brand }.ifBlank { NONE_DETECTED }
    val talentOrNone get() = talent.ifBlank { NONE_DETECTED }
    val dupKey get() = listOf(name.lowercase(Locale.US).trim(), slot, brandOrSet, talent).joinToString("|")

    fun csvRow(): String = listOf(
        source, startSeconds.toString(), endSeconds.toString(), displayName, category, slot, rarity,
        brand, gearSet, talent, core, if (mainStat > 0) mainStat.toString() else "", tag,
        if (favorite) "yes" else "", if (reviewed) "yes" else "", notes, rawText.replace("\n", " | ")
    ).joinToString(",") { csvEscape(it) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("source", source); put("startMs", startMs); put("endMs", endMs)
        put("rawText", rawText); put("name", name); put("category", category); put("slot", slot)
        put("rarity", rarity); put("brand", brand); put("gearSet", gearSet); put("talent", talent)
        put("core", core); put("mainStat", mainStat); put("tag", tag); put("favorite", favorite)
        put("reviewed", reviewed); put("notes", notes)
    }

    companion object {
        const val CSV_HEADER = "Source video,Start (s),End (s),Item name,Category,Slot / type,Rarity,Brand," +
            "Gear set,Talent,Core attribute,Main stat value,Tag,Favorite,Reviewed,Notes,All recognized text"

        fun fromJson(o: JSONObject) = ScannedItem(
            id = o.optLong("id"), source = o.optString("source"), startMs = o.optLong("startMs"),
            endMs = o.optLong("endMs"), rawText = o.optString("rawText"), name = o.optString("name"),
            category = o.optString("category", UNKNOWN), slot = o.optString("slot", UNKNOWN),
            rarity = o.optString("rarity", UNKNOWN), brand = o.optString("brand"),
            gearSet = o.optString("gearSet"), talent = o.optString("talent"),
            core = o.optString("core", UNKNOWN), mainStat = o.optLong("mainStat"),
            tag = o.optString("tag", "Unsorted"), favorite = o.optBoolean("favorite"),
            reviewed = o.optBoolean("reviewed"), notes = o.optString("notes")
        )
    }
}

fun csvEscape(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

/** Result of reading one item panel's OCR text. */
data class ParsedFields(
    val name: String, val category: String, val slot: String, val rarity: String, val brand: String,
    val gearSet: String, val talent: String, val core: String, val mainStat: Long
)

object ItemParser {
    private fun norm(s: String) = " " + s.lowercase(Locale.US).replace(Regex("[^a-z0-9&]+"), " ").trim() + " "
    private fun hasKey(normText: String, key: String) = normText.contains(" $key ")

    fun parse(raw: String): ParsedFields {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val all = norm(raw)

        // Slot / weapon type: ignore attribute lines such as "+15% Assault Rifle Damage".
        val labelLines = lines.filter { l -> !l.contains('%') && !l.contains('+') && !l.lowercase(Locale.US).contains("damage") }
        val labelText = norm(labelLines.joinToString(" "))
        val slot = Catalog.slotAliases.firstOrNull { hasKey(labelText, it.first) }?.second ?: UNKNOWN
        val category = when (slot) {
            in Catalog.gearSlots -> "Gear"
            in Catalog.weaponTypes -> "Weapon"
            else -> UNKNOWN
        }

        val gearSet = Catalog.gearSets.firstOrNull { hasKey(all, it.first) }?.second ?: ""
        val brand = if (gearSet.isNotEmpty()) "" else Catalog.brands.firstOrNull { hasKey(all, it.first) }?.second ?: ""

        // Talents: match a short line that is (or starts with) the talent name to avoid description text.
        val talent = lines.firstNotNullOfOrNull { line ->
            val n = norm(line).trim()
            if (n.length > 34) return@firstNotNullOfOrNull null
            val perfect = Regex("^perfect(ly)? ([a-z ]+)$").find(n)
            if (perfect != null && Catalog.talents.any { perfect.groupValues[2].trim().startsWith(it) }) {
                line.trim()
            } else Catalog.talents.firstOrNull { n == it || n.startsWith("$it ") }?.let { titleCase(it) }
        } ?: ""

        val rarity = when {
            hasKey(all, "exotic") -> "Exotic"
            gearSet.isNotEmpty() || hasKey(all, "gear set") -> "Gear Set"
            hasKey(all, "named") -> "Named"
            hasKey(all, "high end") || brand.isNotEmpty() -> "High-End"
            hasKey(all, "superior") -> "Superior"
            hasKey(all, "specialized") -> "Specialized"
            hasKey(all, "standard") -> "Standard"
            else -> UNKNOWN
        }

        val core = when {
            category == "Weapon" -> UNKNOWN
            hasKey(all, "skill tier") -> "Skill Tier"
            Regex("(?i)\\b(1[0-5]|[0-9])([.,][0-9])?\\s*%\\s*weapon damage").containsMatchIn(raw) -> "Weapon Damage"
            Regex("(?i)\\b[0-9]{1,3}[,.][0-9]{3}\\s*armou?r\\b").containsMatchIn(raw) -> "Armor"
            else -> UNKNOWN
        }

        val mainStat = Regex("\\b[0-9]{1,3}(?:[,.][0-9]{3})+\\b|\\b[0-9]{4,7}\\b").findAll(raw)
            .mapNotNull { it.value.replace(",", "").replace(".", "").toLongOrNull() }
            .maxOrNull() ?: 0L

        val name = lines.firstOrNull { l ->
            l.length in 3..55 && l.count(Char::isLetter) >= 3 && !l.contains('%') && !Catalog.isUiNoise(l) &&
                Catalog.allSlots.none { it.equals(l, ignoreCase = true) }
        } ?: ""

        return ParsedFields(name, category, slot, rarity, brand, gearSet, talent, core, mainStat)
    }

    fun apply(item: ScannedItem, keepName: Boolean = false) {
        val p = parse(item.rawText)
        if (!keepName || item.name.isBlank()) item.name = p.name
        item.category = p.category; item.slot = p.slot; item.rarity = p.rarity; item.brand = p.brand
        item.gearSet = p.gearSet; item.talent = p.talent; item.core = p.core; item.mainStat = p.mainStat
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

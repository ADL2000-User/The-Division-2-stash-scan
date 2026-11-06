package com.example.division2stashscanner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.coroutines.resume

data class ScannedItem(
    val startSeconds: Int,
    var endSeconds: Int,
    var rawText: String,
    var confirmedName: String = ""
) {
    fun csv(): String {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        fun match(regex: Regex) = lines.firstOrNull { regex.containsMatchIn(it) } ?: ""
        val title = confirmedName.ifBlank {
            lines.firstOrNull { it.length in 3..55 && it.any(Char::isLetter) } ?: "Needs review"
        }
        val brand = match(Regex("(?i)\\b(fenris|overlord|ceska|sokolov|petrov|airaldi|providence|golan|empress|hana-u|wyvern|belstone|gila|badger|richter|walker|umbra|striker|heartbreaker|huntsman|eclipse protocol|ongoing directive|negotiator)\\b"))
        val talent = match(Regex("(?i)\\b(obliterate|unbreakable|glass cannon|vigilance|strained|killer|optimist|ignited|in sync|overwatch|perfect [a-z ]+)\\b"))
        val slot = match(Regex("(?i)\\b(mask|backpack|chest|gloves|holster|kneepads|rifle|assault rifle|smg|lmg|shotgun|marksman rifle|pistol)\\b"))
        val armor = match(Regex("(?i)(armor|armour|\\d{1,3}[,.]?\\d{3}\\s*armor)"))
        return listOf(startSeconds,endSeconds,title,slot,brand,talent,armor,rawText.replace("\n"," | ")).joinToString(",") { csvEscape(it) }
    }
}
private fun csvEscape(s: String) = "\"" + s.replace("\"","\"\"") + "\""

class MainActivity : AppCompatActivity() {
    private val pickVideo = 1001
    private val saveCsv = 1002
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val items = mutableListOf<ScannedItem>()
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var exportButton: Button
    private var selectedUri: Uri? = null
    private var videoDurationSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 26, 24, 20)
            setBackgroundColor(android.graphics.Color.rgb(21,25,29))
        }
        fun label(s: String, size: Float, color: Int = android.graphics.Color.WHITE) =
            TextView(this).apply { text=s; textSize=size; setTextColor(color) }
        root.addView(label("DIVISION 2\nSTASH SCANNER", 25f))
        root.addView(label("XBOX • 4K CAPTURE PROFILE • v0.2", 12f, 0xFFFFB36B.toInt()).apply { setPadding(0,8,0,12) })
        root.addView(label("Record your stash with each item panel visible for 5+ seconds. Import the 30–60 second clip below.", 14f, 0xFFD0D5D9.toInt()).apply { setPadding(0,0,0,14) })
        root.addView(Button(this).apply {
            text="1  •  Choose Xbox stash recording"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type="video/*"
                }, pickVideo)
            }
        })
        scanButton = Button(this).apply { text="2  •  Analyze 4K video"; isEnabled=false }
        root.addView(scanButton)
        exportButton = Button(this).apply { text="3  •  Export reviewed spreadsheet (CSV)"; isEnabled=false
            setOnClickListener { exportCsv() }
        }
        root.addView(exportButton)
        status = label("No recording selected.", 13f, 0xFFFFC58A.toInt()).apply { setPadding(0,12,0,12) }
        root.addView(status)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)

        scanButton.setOnClickListener {
            val uri=selectedUri ?: return@setOnClickListener
            scanButton.isEnabled=false; exportButton.isEnabled=false
            scope.launch {
                items.clear()
                status.text="Preparing 4K video…"
                val scanResult=withContext(Dispatchers.IO) { scanVideo(uri) }
                videoDurationSeconds=scanResult.first
                items.addAll(scanResult.second)
                status.text="Scan complete: ${items.size} candidate item screens from ${videoDurationSeconds}s. Tap any row to review/edit."
                renderItems()
                exportButton.isEnabled=items.isNotEmpty()
                scanButton.isEnabled=true
            }
        }
    }

    private fun renderItems() {
        results.removeAllViews()
        if(items.isEmpty()) {
            results.addView(TextView(this).apply { text="No item panels were detected for at least five seconds. Try a steadier recording with larger readable UI."; setTextColor(-1) })
            return
        }
        items.forEachIndexed { index, item ->
            val title=item.confirmedName.ifBlank { item.rawText.lines().firstOrNull { it.length in 3..55 } ?: "Unidentified item" }
            val row=TextView(this).apply {
                text="${index+1}.  ${item.startSeconds}s–${item.endSeconds}s  •  ${item.endSeconds-item.startSeconds+1}s\n$title\nTap to review OCR and set item name"
                textSize=14f; setTextColor(-1); setPadding(12,14,12,14)
                setBackgroundColor(0xFF303840.toInt())
                setOnClickListener { editItem(index) }
            }
            val lp=LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,8)
            results.addView(row,lp)
        }
    }

    private fun editItem(index:Int) {
        val item=items[index]
        val name=EditText(this).apply {
            hint="Confirmed item name (optional)"
            setText(item.confirmedName)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val raw=EditText(this).apply {
            hint="Correct OCR text here if needed"
            setText(item.rawText)
            minLines=8; maxLines=14
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(24,8,24,0)
            addView(name); addView(raw)
        }
        AlertDialog.Builder(this)
            .setTitle("Review item ${index+1}")
            .setView(layout)
            .setPositiveButton("Save") { _,_ ->
                item.confirmedName=name.text.toString().trim()
                item.rawText=raw.text.toString()
                renderItems()
            }
            .setNeutralButton("Delete candidate") { _,_ ->
                items.removeAt(index); renderItems(); exportButton.isEnabled=items.isNotEmpty()
            }
            .setNegativeButton("Cancel",null).show()
    }

    private fun exportCsv() {
        val header="Start (s),End (s),Item name (reviewed/estimated),Possible slot,Possible brand or gear set,Possible talent,Possible armor text,All recognized text\n"
        val csv=header+items.joinToString("\n"){it.csv()}
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="text/csv"
            putExtra(Intent.EXTRA_TITLE,"division2_xbox_stash.csv")
        },saveCsv)
        pendingCsv=csv
    }
    private var pendingCsv=""
    @Deprecated("Legacy result API retained for broad Android compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=Activity.RESULT_OK) return
        if(requestCode==pickVideo) {
            selectedUri=data?.data
            scanButton.isEnabled=selectedUri!=null
            status.text=if(selectedUri!=null) "Recording selected. Tap Analyze 4K video." else "No recording selected."
        } else if(requestCode==saveCsv) {
            data?.data?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { out -> OutputStreamWriter(out).use { it.write(pendingCsv) } }
                status.text="CSV exported. Open it in Excel or Google Sheets."
            }
        }
    }

    private suspend fun scanVideo(uri:Uri):Pair<Int,List<ScannedItem>> = withContext(Dispatchers.IO) {
        val retriever=MediaMetadataRetriever()
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val frames=mutableListOf<Pair<Int,String>>()
        var duration=0
        try {
            retriever.setDataSource(this@MainActivity,uri)
            val durationMs=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            duration=(durationMs/1000L).toInt()
            if(durationMs<=0L) return@withContext 0 to emptyList()
            // Xbox 4K clips are decoded at source resolution; only very large frames are reduced
            // to 2560px wide to limit memory while retaining small UI text.
            for(second in 0..(durationMs/1000L).toInt()) {
                val source=retriever.getFrameAtTime(second*1_000_000L,MediaMetadataRetriever.OPTION_CLOSEST)
                if(source!=null) {
                    val bitmap=downscaleForOcr(source)
                    if(bitmap!==source) source.recycle()
                    val text=recognizer.process(InputImage.fromBitmap(bitmap,0)).awaitRecognizedText()
                    val normalized=normalize(text)
                    if(normalized.isNotBlank()) frames.add(second to normalized)
                    bitmap.recycle()
                }
            }
        } finally {
            retriever.release(); recognizer.close()
        }
        duration to consolidate(frames)
    }

    private fun downscaleForOcr(src:Bitmap):Bitmap {
        val maxWidth=2560
        if(src.width<=maxWidth) return src
        val ratio=maxWidth.toFloat()/src.width
        return Bitmap.createScaledBitmap(src,maxWidth,(src.height*ratio).toInt(),true)
    }

    private fun consolidate(frames:List<Pair<Int,String>>):List<ScannedItem> {
        if(frames.isEmpty()) return emptyList()
        val output=mutableListOf<ScannedItem>()
        var start=frames.first().first
        var last=start
        val group=mutableListOf(frames.first().second)
        for((sec,txt) in frames.drop(1)) {
            val same=similarity(group.last(),txt)>=0.58
            if(sec-last<=2 && same) {
                group.add(txt); last=sec
            } else {
                if(last-start>=5) output.add(ScannedItem(start,last,mostComplete(group)))
                start=sec; last=sec; group.clear(); group.add(txt)
            }
        }
        if(last-start>=5) output.add(ScannedItem(start,last,mostComplete(group)))
        return output
    }

    private fun normalize(s:String)=s.lines().map{it.trim().replace(Regex("\\s+")," ")}
        .filter{it.length>1}.distinct().joinToString("\n")
    private fun similarity(a:String,b:String):Double {
        val aa=a.lowercase(Locale.US).split(Regex("\\W+")).filter{it.length>2}.toSet()
        val bb=b.lowercase(Locale.US).split(Regex("\\W+")).filter{it.length>2}.toSet()
        if(aa.isEmpty()||bb.isEmpty()) return 0.0
        return aa.intersect(bb).size.toDouble()/aa.union(bb).size
    }
    private fun mostComplete(texts:List<String>)=texts.maxByOrNull { it.length } ?: ""
    private suspend fun com.google.android.gms.tasks.Task<Text>.awaitRecognizedText():String =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if(cont.isActive) cont.resume(it.text) }
            addOnFailureListener { if(cont.isActive) cont.resume("") }
        }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

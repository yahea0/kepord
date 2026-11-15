package com.kepord.ime.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kepord.ime.data.KepordPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ClipEntry(
    val id: String,
    val preview: String,
    val charCount: Int,
    val lineCount: Int,
    val byteSize: Long,
    val contentHash: String,
    val fileName: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

class ClipboardStore(
    context: Context,
    private val preferences: KepordPreferences
) {
    private val appContext = context.applicationContext
    private val clipsDir = File(appContext.filesDir, "clipboard_blobs").apply { mkdirs() }
    private val dbHelper = ClipDbHelper(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbLock = Any()

    private val _entries = MutableStateFlow<List<ClipEntry>>(emptyList())
    val entries: StateFlow<List<ClipEntry>> = _entries.asStateFlow()

    @Volatile
    private var lastCapturedHash: String? = null

    fun reloadAsync() {
        scope.launch {
            val loaded = queryAllInternal()
            lastCapturedHash = loaded.firstOrNull()?.contentHash
            _entries.value = loaded
        }
    }

    fun captureFromSystemClipboard(clipboardManager: ClipboardManager?) {
        if (clipboardManager == null) return
        val cfg = preferences.current()
        if (!cfg.clipboardMonitorEnabled) return

        val clip: ClipData = try {
            if (!clipboardManager.hasPrimaryClip()) return
            clipboardManager.primaryClip ?: return
        } catch (_: Throwable) {
            return
        }

        if (clip.itemCount <= 0) return

        if (cfg.clipboardSkipSensitive) {
            val sensitive = clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true
            if (sensitive) return
        }

        val item = clip.getItemAt(0)
        val rawText = item.text?.toString()
            ?: item.coerceToText(appContext)?.toString()
            ?: return

        if (rawText.isEmpty()) return
        saveTextAsync(rawText)
    }

    fun saveTextAsync(rawText: String) {
        if (rawText.isEmpty()) return
        scope.launch {
            saveTextBlocking(rawText)
        }
    }

    suspend fun saveTextBlocking(rawText: String) = withContext(Dispatchers.IO) {
        if (rawText.isEmpty()) return@withContext
        val hash = sha256(rawText)
        val now = System.currentTimeMillis()

        synchronized(dbLock) {
            val db = dbHelper.writableDatabase
            val existing = findByHash(db, hash)
            if (existing != null) {
                // Verify backing file still exists; recreate if needed
                val file = File(clipsDir, existing.fileName)
                if (!file.exists()) {
                    file.writeText(rawText, Charsets.UTF_8)
                }
                val cv = ContentValues().apply {
                    put("updated_at", now)
                }
                db.update(TABLE_CLIPS, cv, "id = ?", arrayOf(existing.id))
                lastCapturedHash = hash
                refreshFlowLocked(db)
                return@synchronized
            }

            val id = UUID.randomUUID().toString()
            val fileName = "$id.txt"
            val blobFile = File(clipsDir, fileName)
            blobFile.writeText(rawText, Charsets.UTF_8)

            val preview = buildPreview(rawText)
            val lines = countLines(rawText)
            val byteSize = blobFile.length()

            val cv = ContentValues().apply {
                put("id", id)
                put("preview", preview)
                put("char_count", rawText.length)
                put("line_count", lines)
                put("byte_size", byteSize)
                put("content_hash", hash)
                put("file_name", fileName)
                put("is_pinned", 0)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow(TABLE_CLIPS, null, cv)

            pruneUnpinnedLocked(db, preferences.current().clipboardMaxUnpinned)
            lastCapturedHash = hash
            refreshFlowLocked(db)
        }
    }

    suspend fun readFullText(entry: ClipEntry): String = withContext(Dispatchers.IO) {
        val file = File(clipsDir, entry.fileName)
        if (!file.exists()) return@withContext entry.preview
        return@withContext file.readText(Charsets.UTF_8)
    }

    fun readFullTextSync(entry: ClipEntry): String {
        val file = File(clipsDir, entry.fileName)
        return if (file.exists()) file.readText(Charsets.UTF_8) else entry.preview
    }

    fun togglePinAsync(id: String) {
        scope.launch {
            synchronized(dbLock) {
                val db = dbHelper.writableDatabase
                db.execSQL(
                    "UPDATE $TABLE_CLIPS SET is_pinned = CASE WHEN is_pinned = 1 THEN 0 ELSE 1 END WHERE id = ?",
                    arrayOf(id)
                )
                refreshFlowLocked(db)
            }
        }
    }

    fun deleteEntryAsync(entry: ClipEntry) {
        scope.launch {
            synchronized(dbLock) {
                val db = dbHelper.writableDatabase
                db.delete(TABLE_CLIPS, "id = ?", arrayOf(entry.id))
                File(clipsDir, entry.fileName).delete()
                refreshFlowLocked(db)
            }
        }
    }

    fun clearUnpinnedAsync() {
        scope.launch {
            synchronized(dbLock) {
                val db = dbHelper.writableDatabase
                val filesToDelete = mutableListOf<String>()
                db.rawQuery("SELECT file_name FROM $TABLE_CLIPS WHERE is_pinned = 0", null).use { c ->
                    while (c.moveToNext()) {
                        filesToDelete.add(c.getString(0))
                    }
                }
                db.delete(TABLE_CLIPS, "is_pinned = 0", null)
                filesToDelete.forEach { File(clipsDir, it).delete() }
                refreshFlowLocked(db)
            }
        }
    }

    fun enforceLimitAsync() {
        scope.launch {
            synchronized(dbLock) {
                val db = dbHelper.writableDatabase
                pruneUnpinnedLocked(db, preferences.current().clipboardMaxUnpinned)
                refreshFlowLocked(db)
            }
        }
    }

    private fun pruneUnpinnedLocked(db: SQLiteDatabase, maxUnpinned: Int) {
        val keep = maxUnpinned.coerceAtLeast(1)
        val stale = mutableListOf<Pair<String, String>>()
        val sql = """
            SELECT id, file_name FROM $TABLE_CLIPS
            WHERE is_pinned = 0
            ORDER BY updated_at DESC
            LIMIT -1 OFFSET ?
        """.trimIndent()
        db.rawQuery(sql, arrayOf(keep.toString())).use { c ->
            while (c.moveToNext()) {
                stale.add(c.getString(0) to c.getString(1))
            }
        }
        for ((id, fileName) in stale) {
            db.delete(TABLE_CLIPS, "id = ?", arrayOf(id))
            File(clipsDir, fileName).delete()
        }
    }

    private fun refreshFlowLocked(db: SQLiteDatabase) {
        _entries.value = readAllFromDb(db)
    }

    private fun queryAllInternal(): List<ClipEntry> {
        synchronized(dbLock) {
            return readAllFromDb(dbHelper.readableDatabase)
        }
    }

    private fun readAllFromDb(db: SQLiteDatabase): List<ClipEntry> {
        val result = mutableListOf<ClipEntry>()
        val sql = """
            SELECT id, preview, char_count, line_count, byte_size, content_hash, file_name, is_pinned, created_at, updated_at
            FROM $TABLE_CLIPS
            ORDER BY is_pinned DESC, updated_at DESC
        """.trimIndent()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                result.add(
                    ClipEntry(
                        id = c.getString(0),
                        preview = c.getString(1),
                        charCount = c.getInt(2),
                        lineCount = c.getInt(3),
                        byteSize = c.getLong(4),
                        contentHash = c.getString(5),
                        fileName = c.getString(6),
                        isPinned = c.getInt(7) == 1,
                        createdAt = c.getLong(8),
                        updatedAt = c.getLong(9)
                    )
                )
            }
        }
        return result
    }

    private fun findByHash(db: SQLiteDatabase, hash: String): ClipEntry? {
        val sql = """
            SELECT id, preview, char_count, line_count, byte_size, content_hash, file_name, is_pinned, created_at, updated_at
            FROM $TABLE_CLIPS WHERE content_hash = ? LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(hash)).use { c ->
            if (c.moveToFirst()) {
                return ClipEntry(
                    id = c.getString(0),
                    preview = c.getString(1),
                    charCount = c.getInt(2),
                    lineCount = c.getInt(3),
                    byteSize = c.getLong(4),
                    contentHash = c.getString(5),
                    fileName = c.getString(6),
                    isPinned = c.getInt(7) == 1,
                    createdAt = c.getLong(8),
                    updatedAt = c.getLong(9)
                )
            }
        }
        return null
    }

    private fun buildPreview(text: String): String {
        val clean = if (text.length > 260) text.substring(0, 260) else text
        return clean.replace('\r', ' ').replace('\n', '↵').trim()
    }

    private fun countLines(text: String): Int {
        var count = 1
        for (i in 0 until text.length) {
            if (text[i] == '\n') count++
        }
        return count
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = text.toByteArray(Charsets.UTF_8)
        val digest = md.digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private class ClipDbHelper(context: Context) : SQLiteOpenHelper(
        context,
        DB_NAME,
        null,
        DB_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CLIPS (
                    id TEXT PRIMARY KEY,
                    preview TEXT NOT NULL,
                    char_count INTEGER NOT NULL,
                    line_count INTEGER NOT NULL,
                    byte_size INTEGER NOT NULL,
                    content_hash TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    is_pinned INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_clips_hash ON $TABLE_CLIPS(content_hash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_clips_order ON $TABLE_CLIPS(is_pinned DESC, updated_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val DB_NAME = "kepord_clipboard.db"
        private const val DB_VERSION = 1
        private const val TABLE_CLIPS = "clips"
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private val HEX = "0123456789abcdef".toCharArray()
    }
}

app/src/main/java/com/kepord/ime/clipboard/BootReceiver.kt

package com.kepord.ime.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kepord.ime.KepordApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? KepordApplication
            app?.clipboardStore?.reloadAsync()
        }
    }
}

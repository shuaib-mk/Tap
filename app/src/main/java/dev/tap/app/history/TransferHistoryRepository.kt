package dev.tap.app.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.protocol.TransferDirection
import dev.tap.app.protocol.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Robust local SQLite history repository.
 * Zero annotation-processing, instant compilation, 100% thread-safe.
 */
class TransferHistoryRepository private constructor(context: Context) {
    private val dbHelper = DatabaseHelper(context.applicationContext)
    private val _records = MutableStateFlow<List<TransferRecord>>(emptyList())
    val records: StateFlow<List<TransferRecord>> = _records.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val list = loadAllFromDb()
        _records.value = list
    }

    private fun loadAllFromDb(): List<TransferRecord> {
        val list = mutableListOf<TransferRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val record = TransferRecord(
                    id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                    transferId = c.getString(c.getColumnIndexOrThrow(COL_TRANSFER_ID)),
                    fileName = c.getString(c.getColumnIndexOrThrow(COL_FILE_NAME)),
                    fileSize = c.getLong(c.getColumnIndexOrThrow(COL_FILE_SIZE)),
                    mimeType = c.getString(c.getColumnIndexOrThrow(COL_MIME_TYPE)),
                    direction = TransferDirection.valueOf(c.getString(c.getColumnIndexOrThrow(COL_DIRECTION))),
                    status = TransferStatus.valueOf(c.getString(c.getColumnIndexOrThrow(COL_STATUS))),
                    peerName = c.getString(c.getColumnIndexOrThrow(COL_PEER_NAME)),
                    connectionMethod = c.getString(c.getColumnIndexOrThrow(COL_CONNECTION_METHOD)),
                    timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    filePath = c.getString(c.getColumnIndexOrThrow(COL_FILE_PATH))
                )
                list.add(record)
            }
        }
        return list
    }

    suspend fun insert(record: TransferRecord): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_TRANSFER_ID, record.transferId)
            put(COL_FILE_NAME, record.fileName)
            put(COL_FILE_SIZE, record.fileSize)
            put(COL_MIME_TYPE, record.mimeType)
            put(COL_DIRECTION, record.direction.name)
            put(COL_STATUS, record.status.name)
            put(COL_PEER_NAME, record.peerName)
            put(COL_CONNECTION_METHOD, record.connectionMethod)
            put(COL_TIMESTAMP, record.timestamp)
            put(COL_FILE_PATH, record.filePath)
        }
        val id = db.insert(TABLE_NAME, null, values)
        refresh()
        id
    }

    suspend fun updateStatus(transferId: String, status: TransferStatus) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_STATUS, status.name)
        }
        db.update(TABLE_NAME, values, "$COL_TRANSFER_ID = ?", arrayOf(transferId))
        refresh()
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
        refresh()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, null, null)
        refresh()
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TRANSFER_ID TEXT NOT NULL,
                    $COL_FILE_NAME TEXT NOT NULL,
                    $COL_FILE_SIZE INTEGER NOT NULL,
                    $COL_MIME_TYPE TEXT NOT NULL,
                    $COL_DIRECTION TEXT NOT NULL,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_PEER_NAME TEXT NOT NULL,
                    $COL_CONNECTION_METHOD TEXT NOT NULL,
                    $COL_TIMESTAMP INTEGER NOT NULL,
                    $COL_FILE_PATH TEXT
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "tap_history.db"
        private const val DB_VERSION = 1
        private const val TABLE_NAME = "transfers"

        private const val COL_ID = "id"
        private const val COL_TRANSFER_ID = "transfer_id"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_FILE_SIZE = "file_size"
        private const val COL_MIME_TYPE = "mime_type"
        private const val COL_DIRECTION = "direction"
        private const val COL_STATUS = "status"
        private const val COL_PEER_NAME = "peer_name"
        private const val COL_CONNECTION_METHOD = "connection_method"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_FILE_PATH = "file_path"

        @Volatile
        private var INSTANCE: TransferHistoryRepository? = null

        fun getInstance(context: Context): TransferHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransferHistoryRepository(context).also { INSTANCE = it }
            }
        }
    }
}

package com.example.bajeti.data

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val BATCH_SIZE = 100

data class DeviceSmsMessage(val id: Long, val body: String, val timestampMillis: Long)

data class SmsImportResult(
    val found: Int = 0,
    val batchCount: Int = 0,
    val created: Int = 0,
    val duplicates: Int = 0,
    val ignored: Int = 0,
    val failed: Int = 0,
    val error: String? = null,
)

class SmsRepository(
    private val context: Context,
    private val prefs: SmsPreferences,
    private val api: SmsApiService,
) {
    private val importLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(sender: String): Mutex =
        importLocks.computeIfAbsent(normalizeSender(sender)) { Mutex() }

    fun isImporting(sender: String): Boolean = lockFor(sender).isLocked

    suspend fun loadNewMessages(sender: String): List<DeviceSmsMessage> {
        val afterId = prefs.getCursor(sender)
        val startAt = prefs.getStartDate(sender)
        return loadMessages(sender, afterId, startAt)
    }

    fun loadSenders(): List<String> {
        val projection = arrayOf(Telephony.Sms.ADDRESS)
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        ) ?: return emptyList()

        // Deduplicate by exact trimmed address — case is preserved so "Equity Bank"
        // and "EQUITY BANK" stay separate (they're distinct ADDRESS values in the provider).
        // Phone numbers still deduplicate because the same digits appear identically.
        val seen = LinkedHashSet<String>()
        cursor.use {
            val col = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (it.moveToNext()) {
                val addr = it.getString(col)?.trim() ?: continue
                if (addr.isNotBlank()) seen.add(addr)
            }
        }
        return seen.toList()
    }

    private fun loadMessages(sender: String, afterId: Long, startAtMillis: Long): List<DeviceSmsMessage> {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE)

        // Both filters applied together — cursor narrows range, start date adds date floor
        val selectionParts = mutableListOf("${Telephony.Sms.ADDRESS} = ?")
        val selectionArgs = mutableListOf(sender)

        if (afterId > -1L) {
            selectionParts.add("${Telephony.Sms._ID} > ?")
            selectionArgs.add(afterId.toString())
        }
        if (startAtMillis > -1L) {
            selectionParts.add("${Telephony.Sms.DATE} >= ?")
            selectionArgs.add(startAtMillis.toString())
        }

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            "${Telephony.Sms._ID} ASC",
        ) ?: return emptyList()

        val result = mutableListOf<DeviceSmsMessage>()
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                result.add(
                    DeviceSmsMessage(
                        id = it.getLong(idCol),
                        body = it.getString(bodyCol) ?: "",
                        timestampMillis = it.getLong(dateCol),
                    )
                )
            }
        }
        return result
    }

    suspend fun importSender(
        sender: String,
        token: String,
        onProgress: suspend (batchDone: Int, batchTotal: Int) -> Unit,
    ): SmsImportResult = lockFor(sender).withLock {
        val afterId = prefs.getCursor(sender)
        val startAt = prefs.getStartDate(sender)
        val messages = loadMessages(sender, afterId, startAt)

        if (messages.isEmpty()) return SmsImportResult(found = 0)

        val batches = messages.chunked(BATCH_SIZE)
        var totalCreated = 0
        var totalDuplicates = 0
        var totalIgnored = 0
        var totalFailed = 0

        try {
            for ((index, batch) in batches.withIndex()) {
                val response = api.importBulk(
                    authorization = "Bearer $token",
                    request = SmsBulkRequest(
                        messages = batch.map { it.body },
                        timestamp = batch.last().timestampMillis,
                    ),
                )
                totalCreated += response.summary.created
                totalDuplicates += response.summary.duplicates
                totalIgnored += response.summary.ignored
                totalFailed += response.summary.failed

                // Advance cursor to highest ID in this batch — safe to do per batch
                prefs.saveCursor(sender, batch.maxOf { it.id })
                onProgress(index + 1, batches.size)
            }
        } catch (e: Exception) {
            return SmsImportResult(
                found = messages.size,
                batchCount = batches.size,
                created = totalCreated,
                duplicates = totalDuplicates,
                ignored = totalIgnored,
                failed = totalFailed,
                error = e.message ?: "Network error",
            )
        }

        return SmsImportResult(
            found = messages.size,
            batchCount = batches.size,
            created = totalCreated,
            duplicates = totalDuplicates,
            ignored = totalIgnored,
            failed = totalFailed,
        )
    }
}

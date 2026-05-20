package com.example.bajeti.data

data class SmsBulkRequest(
    val messages: List<String>,
    val timestamp: Long? = null,
    val includeFeeInExpense: Boolean = false,
)

data class SmsBulkResults(
    val created: Int,
    val duplicates: Int,
    val ignored: Int,
    val failed: Int,
)

data class SmsBulkResponse(
    val summary: SmsBulkResults,
)

data class DeleteTransactionResponse(
    val ok: Boolean,
)

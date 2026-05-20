package com.example.bajeti.data

data class SmsPreviewRequest(
    val message: String,
    val timestamp: Long,
)

data class PreviewTransaction(
    val amount: Double,
    val categoryId: String?,
    val date: String,
    val notes: String,
    val type: String,
    val smsCounterparty: String?,
    val smsCounterpartyKey: String?,
)

data class SmsPreviewResponse(
    val status: String,
    val reason: String?,
    val preview: PreviewTransaction?,
)

data class SmsCategory(
    val id: String,
    val name: String,
    val type: String,
    val isDefault: Boolean,
)

data class CreateTransactionRequest(
    val amount: Double,
    val categoryId: String,
    val date: String,
    val notes: String,
    val type: String,
    val idempotencyKey: String,
)

data class UpdateTransactionRequest(
    val amount: Double,
    val categoryId: String,
    val date: String,
    val notes: String,
    val type: String,
)

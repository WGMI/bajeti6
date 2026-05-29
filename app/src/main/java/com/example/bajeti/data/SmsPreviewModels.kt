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
    val smsIdempotencyKey: String?,
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
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
)

data class CreateTransactionResponse(
    val id: String,
    val status: String,
    val message: String? = null,
)

data class UpdateTransactionRequest(
    val amount: Double,
    val categoryId: String,
    val date: String,
    val notes: String,
    val type: String,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
)

data class CreateAccountRequest(val name: String)

data class UpdateAccountRequest(val name: String)

data class DeleteAccountResponse(val ok: Boolean)

data class CreateCategoryRequest(
    val name: String,
    val type: String,
)

data class UpdateCategoryRequest(
    val name: String,
    val type: String,
)

/** Sent as body for DELETE /api/categories/{id} when the category has transactions. */
data class DeleteCategoryRequest(
    val reassignToCategoryId: String? = null,
    val deleteTransactions: Boolean? = null,
)

data class DeleteCategoryResponse(val ok: Boolean)

/** Shape of the 409 error body when a category still has transactions. */
data class DeleteCategoryConflictResponse(
    val transactionCount: Int? = null,
)

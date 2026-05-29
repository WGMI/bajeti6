package com.example.bajeti.data

import com.google.gson.annotations.SerializedName

data class Period(
    val label: String,
    val dateRange: String,
)

data class MonthStats(
    val income: Double,
    val expenses: Double,
    val balance: Double,
    val transactionsCount: Int,
)

data class TrendEntry(
    val month: String,
    val income: Double,
    val expenses: Double,
    val balance: Double,
)

data class CategoryExpense(
    val category: String?,
    val amount: Double,
)

data class SummaryResponse(
    val period: Period,
    val currentMonth: MonthStats,
    val allTime: MonthStats,
    val trend: List<TrendEntry>,
    val expenseByCategory: List<CategoryExpense>,
)

data class SettingsOptionsResponse(
    val currency: List<String>,
    val dateFormat: List<String>,
    val firstDayOfWeek: List<String>,
    val theme: List<String>,
)

data class Account(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val balance: Double,
)

data class Transaction(
    val id: String,
    val amount: Double,
    @SerializedName("categoryName")
    val category: String?,
    val categoryId: String?,
    val date: String,
    val notes: String?,
    val type: String,
    val accountId: String?,
    val accountName: String?,
    val transferGroupId: String?,
    val transferLeg: String?,
    val counterAccountId: String?,
    val counterAccountName: String?,
    val smsCounterparty: String?,
    val smsCounterpartyKey: String?,
)

data class TransactionsResponse(
    val transactions: List<Transaction>,
    val nextCursor: String?,
    val totalIncome: Double,
    val totalExpense: Double,
)

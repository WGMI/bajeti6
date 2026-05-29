package com.example.bajeti.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface SmsApiService {
    @POST("api/sms/bulk")
    suspend fun importBulk(
        @Header("Authorization") authorization: String,
        @Body request: SmsBulkRequest,
    ): SmsBulkResponse

    @POST("api/sms/preview")
    suspend fun previewSms(
        @Header("Authorization") authorization: String,
        @Body request: SmsPreviewRequest,
    ): SmsPreviewResponse

    @GET("api/sms/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String,
    ): List<SmsCategory>

    @POST("api/transactions")
    suspend fun createTransaction(
        @Header("Authorization") authorization: String,
        @Body request: CreateTransactionRequest,
    ): CreateTransactionResponse

    @PATCH("api/transactions/{id}")
    suspend fun updateTransaction(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: UpdateTransactionRequest,
    ): Transaction

    @DELETE("api/transactions/{id}")
    suspend fun deleteTransaction(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): DeleteTransactionResponse
}

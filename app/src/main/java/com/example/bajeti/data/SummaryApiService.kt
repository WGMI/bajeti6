package com.example.bajeti.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SummaryApiService {
    @GET("api/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String,
    ): List<SmsCategory>

    @GET("api/summary")
    suspend fun getSummary(
        @Header("Authorization") authorization: String,
        @Query("month") month: String,
        @Query("trendMonths") trendMonths: Int = 6,
    ): SummaryResponse

    @GET("api/settings/options")
    suspend fun getSettingsOptions(
        @Header("Authorization") authorization: String,
    ): SettingsOptionsResponse

    @GET("api/transactions")
    suspend fun getTransactions(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("type") type: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("search") search: String? = null,
    ): TransactionsResponse
}

object ApiClient {
    const val BASE_URL = "https://bajeti-v2.vercel.app/"

    private val retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val summaryApi: SummaryApiService by lazy { retrofit.create(SummaryApiService::class.java) }
    val smsApi: SmsApiService by lazy { retrofit.create(SmsApiService::class.java) }
}

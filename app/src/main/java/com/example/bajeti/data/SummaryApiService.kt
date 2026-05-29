package com.example.bajeti.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SummaryApiService {
    @GET("api/accounts")
    suspend fun getAccounts(
        @Header("Authorization") authorization: String,
    ): List<Account>

    @POST("api/accounts")
    suspend fun createAccount(
        @Header("Authorization") authorization: String,
        @Body request: CreateAccountRequest,
    ): Account

    @PATCH("api/accounts/{id}")
    suspend fun updateAccount(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: UpdateAccountRequest,
    ): Account

    @DELETE("api/accounts/{id}")
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): DeleteAccountResponse

    @GET("api/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String,
    ): List<SmsCategory>

    @POST("api/categories")
    suspend fun createCategory(
        @Header("Authorization") authorization: String,
        @Body request: CreateCategoryRequest,
    ): SmsCategory

    @PATCH("api/categories/{id}")
    suspend fun updateCategory(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: UpdateCategoryRequest,
    ): SmsCategory

    /** DELETE with optional body: empty body for no-transaction categories;
     *  body with reassignToCategoryId or deleteTransactions=true when 409. */
    @HTTP(method = "DELETE", path = "api/categories/{id}", hasBody = true)
    suspend fun deleteCategory(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: DeleteCategoryRequest,
    ): DeleteCategoryResponse

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
        @Query("accountId") accountId: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("search") search: String? = null,
    ): TransactionsResponse
}

private class RetryInterceptor(private val maxAttempts: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastResponse: Response? = null
        while (attempt < maxAttempts) {
            if (attempt > 0) Thread.sleep(1000L shl (attempt - 1)) // 1s, 2s
            try {
                val response = chain.proceed(chain.request())
                if (response.code < 500) return response
                lastResponse?.close()
                lastResponse = response
            } catch (e: IOException) {
                if (attempt == maxAttempts - 1) throw e
            }
            attempt++
        }
        return lastResponse!!
    }
}

object ApiClient {
    const val BASE_URL = "https://bajeti-v2.vercel.app/"

    private val retrofit by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())
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

package com.kisanbandhu.app

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://kisanbandhu-backend.onrender.com/"
    private const val GOV_BASE_URL = "https://api.data.gov.in/"
    // UPDATED: Using router.huggingface.co to resolve the 410 Gone error and improve reliability
    private const val HF_BASE_URL = "https://router.huggingface.co/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val marketApi: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GOV_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val hfApi: HuggingFaceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(HF_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HuggingFaceApiService::class.java)
    }
}
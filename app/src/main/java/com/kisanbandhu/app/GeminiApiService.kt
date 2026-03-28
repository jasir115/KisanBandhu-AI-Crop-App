package com.kisanbandhu.app

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {
    // We will dynamically try both v1 and v1beta in the ViewModel
    @POST("{version}/models/{model}:generateContent")
    fun generateContent(
        @Path("version") version: String,
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Call<GeminiResponse>
}

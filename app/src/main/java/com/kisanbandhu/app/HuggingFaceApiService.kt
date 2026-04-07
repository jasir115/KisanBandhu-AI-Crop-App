package com.kisanbandhu.app

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface HuggingFaceApiService {
    @POST("v1/chat/completions")
    fun chatCompletion(
        @Header("Authorization") token: String,
        @Body request: HFChatRequest
    ): Call<HFChatResponse>
}

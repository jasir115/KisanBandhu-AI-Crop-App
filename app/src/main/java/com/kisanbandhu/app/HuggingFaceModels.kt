package com.kisanbandhu.app

import com.google.gson.annotations.SerializedName

data class HFMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class HFChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<HFMessage>,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 500,
    @SerializedName("stream") val stream: Boolean = false
)

data class HFChatResponse(
    @SerializedName("choices") val choices: List<HFChoice>
)

data class HFChoice(
    @SerializedName("message") val message: HFMessage
)

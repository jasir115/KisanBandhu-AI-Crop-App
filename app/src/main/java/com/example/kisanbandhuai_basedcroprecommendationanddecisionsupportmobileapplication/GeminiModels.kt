package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: GeminiContent?
)

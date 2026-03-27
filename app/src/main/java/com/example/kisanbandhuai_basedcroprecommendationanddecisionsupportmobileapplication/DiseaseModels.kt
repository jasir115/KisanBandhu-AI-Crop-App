package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

data class DiseaseResponse(
    val success: Boolean,
    val disease_name: String,
    val confidence: Float,
    val hindi_name: String? = null,
    val description: String? = null,
    val symptoms: String? = null,
    val prevention: String? = null,
    val treatment: String? = null,
    val severity: String? = "Moderate"
)

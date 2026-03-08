package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

data class DiseaseResponse(
    val success: Boolean,
    val disease_name: String,
    val confidence: Float,
    val treatment: String? = null
)
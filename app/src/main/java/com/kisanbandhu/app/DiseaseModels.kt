package com.kisanbandhu.app

data class DiseaseResponse(
    val success: Boolean,
    val disease_name: String,
    val confidence: Float,
    val crop_type: String? = null,
    val hindi_name: String? = null,
    val description: String? = null,
    val symptoms: String? = null,
    val prevention: String? = null,
    val treatment: String? = null,
    val severity: String? = "Moderate",
    val isRejected: Boolean = false
)

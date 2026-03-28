package com.kisanbandhu.app

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val language: String = "English",
    val profileImageUrl: String = "",
    val mobileNumber: String = "",
    val location: String = "",
    val alternateMobile: String = "",
    val email: String = "",
    val address: String = "",
    
    // Farm Information
    val farmSize: Double = 0.0,
    val farmSizeUnit: String = "Acres",
    val farmLocation: String = "",
    val soilType: String = "",
    val irrigationMethod: String = "",
    val ownershipType: String = "",
    val currentCrops: String = ""
)
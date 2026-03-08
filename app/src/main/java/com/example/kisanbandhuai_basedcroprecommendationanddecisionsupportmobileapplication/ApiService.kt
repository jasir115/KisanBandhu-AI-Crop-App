package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @POST("/predict")
    fun getCrop(@Body request: CropRequest): Call<CropResponse>

    @Multipart
    @POST("/predict_disease")
    fun predictDisease(
        @Part image: MultipartBody.Part
    ): Call<DiseaseResponse>
}
package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CropHealthViewModel : ViewModel() {

    private val _diseaseResult = MutableLiveData<DiseaseResponse?>()
    val diseaseResult: LiveData<DiseaseResponse?> = _diseaseResult

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun scanCrop(image: MultipartBody.Part) {
        _isLoading.value = true
        RetrofitClient.api.predictDisease(image).enqueue(object : Callback<DiseaseResponse> {
            override fun onResponse(call: Call<DiseaseResponse>, response: Response<DiseaseResponse>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    _diseaseResult.value = response.body()
                } else {
                    _error.value = "Scanner Error: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<DiseaseResponse>, t: Throwable) {
                _isLoading.value = false
                _error.value = "Connection Failed: ${t.message}"
            }
        })
    }
}
package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.nio.FloatBuffer
import java.nio.DoubleBuffer

class CropRecommendationViewModel(application: Application) : AndroidViewModel(application) {

    // === CONFIGURATION CHECKPOINT ===
    // Set this to TRUE to use the Offline ONNX model.
    // Set this to FALSE to use the Cloud API via Retrofit/Render.
    private val useOfflineModel = true 

    private val _predictionResult = MutableLiveData<CropResponse?>()
    val predictionResult: LiveData<CropResponse?> = _predictionResult

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun predictCrop(request: CropRequest) {
        _isLoading.value = true

        if (useOfflineModel) {
            predictCropOffline(request)
        } else {
            predictCropOnline(request)
        }
    }

    private fun predictCropOnline(request: CropRequest) {
        RetrofitClient.api.getCrop(request).enqueue(object : Callback<CropResponse> {
            override fun onResponse(call: Call<CropResponse>, response: Response<CropResponse>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    _predictionResult.value = response.body()
                } else {
                    _error.value = "Server Error: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<CropResponse>, t: Throwable) {
                _isLoading.value = false
                _error.value = "Connection Failed: ${t.message}"
            }
        })
    }

    private fun predictCropOffline(request: CropRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val env = OrtEnvironment.getEnvironment()
                val assetManager = getApplication<Application>().assets
                val modelBytes = assetManager.open("crop_model.onnx").readBytes()
                val session = env.createSession(modelBytes)

                val inputName = session.inputNames.iterator().next()
                val inputInfo = session.inputInfo[inputName]?.info
                
                Log.d("ONNX_DEBUG", "Model expects input: $inputName")
                Log.d("ONNX_DEBUG", "Model input info: $inputInfo")

                // Convert input to Double array (Most common cause of INVALID_ARGUMENT is Float vs Double)
                val doubleArray = doubleArrayOf(
                    request.N.toDouble(), request.P.toDouble(), request.K.toDouble(),
                    request.temperature.toDouble(), request.humidity.toDouble(), request.ph.toDouble(), request.rainfall.toDouble()
                )
                
                // Also create float fallback
                val floatArray = floatArrayOf(
                    request.N, request.P, request.K,
                    request.temperature, request.humidity, request.ph, request.rainfall
                )

                // Check the expected type from the model
                val isDouble = inputInfo.toString().contains("DOUBLE")
                
                val inputTensor = if (isDouble) {
                    val doubleBuffer = DoubleBuffer.wrap(doubleArray)
                    OnnxTensor.createTensor(env, doubleBuffer, longArrayOf(1, 7))
                } else {
                    val floatBuffer = FloatBuffer.wrap(floatArray)
                    OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 7))
                }

                val results = session.run(mapOf(inputName to inputTensor))
                val output = results[0].value
                
                var predictedCrop = "Unknown"
                
                if (output is Array<*>) {
                    if (output.isArrayOf<String>()) {
                        predictedCrop = (output as Array<String>)[0]
                    } else if (output.isArrayOf<Long>()) {
                        predictedCrop = "Crop ID: ${(output as Array<Long>)[0]}"
                    } else {
                        val firstElem = output[0]
                        if (firstElem is Array<*>) {
                            predictedCrop = firstElem[0].toString()
                        } else {
                            predictedCrop = output[0].toString()
                        }
                    }
                } else if (output is LongArray) {
                    predictedCrop = "Crop ID: ${output[0]}"
                } else {
                    predictedCrop = output.toString().replace(Regex("[\\[\\]]"), "")
                }
                
                results.close()
                inputTensor.close()
                session.close()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _predictionResult.value = CropResponse(success = true, recommended_crop = predictedCrop)
                }

            } catch (e: Exception) {
                Log.e("ONNX_ERROR", "Error running model", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _error.value = "Model Error: ${e.message}"
                }
            }
        }
    }
}
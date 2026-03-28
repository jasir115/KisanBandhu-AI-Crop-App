package com.kisanbandhu.app

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.awaitResponse

class CropHealthViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CropHealthVM"
    private val _diseaseResult = MutableLiveData<DiseaseResponse?>()
    val diseaseResult: LiveData<DiseaseResponse?> = _diseaseResult

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()
    private var localDiseaseDb: JSONObject? = null
    
    private val db = FirebaseFirestore.getInstance()

    // Multi-Stage Strategy to bypass regional 404s
    private val apiStrategies = listOf(
        Pair("v1beta", "gemini-1.5-flash"),
        Pair("v1", "gemini-1.5-flash"),
        Pair("v1beta", "gemini-pro"),
        Pair("v1", "gemini-pro")
    )

    init {
        loadModelAndLabels()
        loadLocalDatabase()
    }

    private fun loadLocalDatabase() {
        try {
            val json = getApplication<Application>().assets.open("disease_info.json").bufferedReader().use { it.readText() }
            localDiseaseDb = JSONObject(json)
            Log.d(TAG, "Local Database Loaded: ${localDiseaseDb?.length()} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Local DB failed: ${e.message}")
        }
    }

    private fun loadModelAndLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelBuffer = loadModelFile("plant_disease_model.tflite")
                val options = Interpreter.Options().setNumThreads(4)
                interpreter = Interpreter(modelBuffer, options)
                
                val labelsJson = getApplication<Application>().assets.open("labels.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(labelsJson)
                val tempList = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    tempList.add(jsonArray.getString(i))
                }
                labels = tempList
                Log.d(TAG, "AI Core ready. Labels: ${labels.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Model init error: ${e.message}")
                _error.postValue("Scanner initialization failed")
            }
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = getApplication<Application>().assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun scanCropOffline(bitmap: Bitmap) {
        _isLoading.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (interpreter == null) {
                    _error.postValue("Scanner not ready")
                    return@launch
                }

                // Preprocessing
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(127.5f, 127.5f))
                    .build()

                var tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(bitmap)
                tensorImage = imageProcessor.process(tensorImage)

                // Inference
                val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)
                interpreter?.run(tensorImage.buffer, outputBuffer.buffer)

                // Post-processing
                val probabilities = outputBuffer.floatArray
                var maxIdx = 0
                var maxProb = 0f
                for (i in probabilities.indices) {
                    if (probabilities[i] > maxProb) {
                        maxProb = probabilities[i]
                        maxIdx = i
                    }
                }

                val detectedLabel = labels.getOrElse(maxIdx) { "Unknown" }
                val formattedName = detectedLabel.replace("___", " ").replace("_", " ")
                
                // Fetch high-quality data from LOCAL database first (Safety fallback)
                val localInfo = localDiseaseDb?.optJSONObject(detectedLabel)
                val initialResult = DiseaseResponse(
                    success = true,
                    disease_name = formattedName,
                    confidence = maxProb,
                    hindi_name = localInfo?.optString("hindi_name", "रोग का पता चला"),
                    description = localInfo?.optString("description", "Analysis in progress..."),
                    symptoms = localInfo?.optString("symptoms", "Check leaf surface for spots or color changes."),
                    prevention = localInfo?.optString("prevention", "Maintain field hygiene and proper spacing."),
                    treatment = localInfo?.optString("treatment", "Consult local expert if spread increases."),
                    severity = if (maxProb > 0.85) "High" else "Moderate"
                )

                withContext(Dispatchers.Main) {
                    _diseaseResult.value = initialResult
                }

                // Trigger Online Gemini Enhancement
                if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) {
                    runGeminiIntelligenceCycle(formattedName)
                } else {
                    withContext(Dispatchers.Main) { _isLoading.value = false }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Inference failure: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _error.value = "Scanning failed"
                }
            }
        }
    }

    private fun runGeminiIntelligenceCycle(diseaseName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            for (strategy in apiStrategies) {
                Log.d(TAG, "Trying Gemini strategy: ${strategy.first} / ${strategy.second}")
                val success = attemptGeminiEnhancement(strategy.first, strategy.second, diseaseName)
                if (success) {
                    Log.d(TAG, "Gemini enhancement SUCCESS with ${strategy.second}")
                    return@launch
                }
            }
            Log.e(TAG, "All Gemini attempts failed. Using local advisor fallback.")
            withContext(Dispatchers.Main) { _isLoading.value = false }
        }
    }

    private suspend fun attemptGeminiEnhancement(version: String, model: String, diseaseName: String): Boolean {
        return try {
            val prompt = """
                You are a Senior Plant Pathologist. Analyze: $diseaseName.
                Provide a JSON object with keys: "hindi_name", "description", "symptoms", "prevention", "treatment".
                Translate names to Hindi. Keep advice practical for a rural farmer.
                Return ONLY the JSON. No markdown backticks or explanation.
            """.trimIndent()

            val request = GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt)))))
            val response = RetrofitClient.geminiApi.generateContent(
                version, model, BuildConfig.GEMINI_API_KEY, request
            ).awaitResponse()

            if (response.isSuccessful) {
                val body = response.body()
                var jsonText = body?.candidates?.get(0)?.content?.parts?.get(0)?.text?.trim() ?: ""
                
                // Force strip any markdown
                if (jsonText.contains("{")) {
                    jsonText = jsonText.substring(jsonText.indexOf("{"), jsonText.lastIndexOf("}") + 1)
                }

                val json = JSONObject(jsonText)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    val current = _diseaseResult.value
                    if (current != null) {
                        _diseaseResult.value = current.copy(
                            hindi_name = json.optString("hindi_name", current.hindi_name),
                            description = json.optString("description", current.description),
                            symptoms = json.optString("symptoms", current.symptoms),
                            prevention = json.optString("prevention", current.prevention),
                            treatment = json.optString("treatment", current.treatment)
                        )
                    }
                }
                true
            } else {
                Log.w(TAG, "Gemini $model failed with code ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini $model exception: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        interpreter?.close()
    }
}

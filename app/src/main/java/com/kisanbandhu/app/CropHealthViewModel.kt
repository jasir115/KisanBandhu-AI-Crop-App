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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.awaitResponse
import kotlin.math.min

class CropHealthViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CropHealthVM"
    private val _diseaseResult = MutableLiveData<DiseaseResponse?>()
    val diseaseResult: LiveData<DiseaseResponse?> = _diseaseResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()
    private var localDiseaseDb: JSONObject? = null
    
    init {
        loadModelAndLabels()
        loadLocalDatabase()
    }

    private fun loadLocalDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = getApplication<Application>().assets.open("disease_info.json").bufferedReader().use { it.readText() }
                localDiseaseDb = JSONObject(json)
                Log.d(TAG, "Local DB loaded successfully with ${localDiseaseDb?.length()} entries")
            } catch (e: Exception) {
                Log.e(TAG, "Local DB failed to load: ${e.message}")
            }
        }
    }

    private fun loadModelAndLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelBuffer = loadModelFile("plant_disease_model2.tflite")
                interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(4))
                
                val labelsJson = getApplication<Application>().assets.open("labels.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(labelsJson)
                val tempList = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) { tempList.add(jsonArray.getString(i)) }
                labels = tempList
            } catch (e: Exception) {
                _error.postValue("Scanner initialization failed")
            }
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = getApplication<Application>().assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun scanCropOffline(bitmap: Bitmap, languageCode: String = "en") {
        _isLoading.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (interpreter == null) {
                    withContext(Dispatchers.Main) { 
                        _isLoading.value = false
                        _error.value = "Model not initialized" 
                    }
                    return@launch
                }

                val size = min(bitmap.width, bitmap.height)
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeWithCropOrPadOp(size, size))
                    .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(127.5f, 127.5f)) 
                    .build()

                var tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(bitmap)
                tensorImage = imageProcessor.process(tensorImage)

                val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)
                interpreter?.run(tensorImage.buffer, outputBuffer.buffer)

                val probabilities = outputBuffer.floatArray
                var maxIdx = 0
                var maxProb = 0f
                for (i in probabilities.indices) { if (probabilities[i] > maxProb) { maxProb = probabilities[i]; maxIdx = i } }

                val detectedLabel = labels.getOrElse(maxIdx) { "Unknown" }
                val isRejected = detectedLabel == "not_a_leaf"
                val isUncertain = maxProb < 0.45f && !isRejected
                
                val labelParts = detectedLabel.split("___")
                val cropType = if (labelParts.size > 1) labelParts[0].replace("_", " ") else "Unknown"
                var displayDiseaseName = detectedLabel.replace("___", " ").replace("_", " ")
                
                if (localDiseaseDb == null) {
                    val json = getApplication<Application>().assets.open("disease_info.json").bufferedReader().use { it.readText() }
                    localDiseaseDb = JSONObject(json)
                }

                val localInfo = if (isUncertain) null else localDiseaseDb?.optJSONObject(detectedLabel)
                
                fun formatLocalText(text: String?): String? {
                    return text?.replace("\n", "<br>")
                }

                val isHindi = languageCode == "hi"

                val initialResult = DiseaseResponse(
                    success = true,
                    disease_name = if (isUncertain) (if (isHindi) "अनिश्चित पहचान" else "Uncertain Detection") else displayDiseaseName,
                    confidence = maxProb,
                    crop_type = if (isUncertain) (if (isHindi) "अज्ञात" else "Unknown") else cropType,
                    hindi_name = localInfo?.optString("hindi_name", if (isUncertain) "अनिश्चित पहचान" else "रोग का पता चला"),
                    description = formatLocalText(localInfo?.optString("description", 
                        if (isUncertain) (if (isHindi) "सटीक विवरण देने के लिए छवि गुणवत्ता बहुत कम है। कृपया बेहतर रोशनी और फोकस के साथ फिर से प्रयास करें।" else "The image quality or confidence is too low to provide accurate details. Please try again with better lighting and focus.") 
                        else (if (isHindi) "विश्लेषण किया जा रहा है..." else "Analyzing..."))),
                    symptoms = formatLocalText(localInfo?.optString("symptoms", 
                        if (isUncertain) (if (isHindi) "कम आत्मविश्वास के कारण किसी विशिष्ट लक्षण की पहचान नहीं की गई।" else "No specific symptoms identified due to low confidence.") 
                        else (if (isHindi) "जाँच हो रही है..." else "Checking..."))),
                    prevention = formatLocalText(localInfo?.optString("prevention", 
                        if (isUncertain) "N/A" 
                        else (if (isHindi) "लोड हो रहा है..." else "Loading..."))),
                    treatment = formatLocalText(localInfo?.optString("treatment", 
                        if (isUncertain) (if (isHindi) "कृपया प्रभावित क्षेत्र पर ध्यान केंद्रित करते हुए फोटो दोबारा लें।" else "Please retake the photo focusing on the affected area.") 
                        else (if (isHindi) "एआई सलाहकार से परामर्श किया जा रहा है..." else "Consulting AI advisor..."))),
                    severity = if (isUncertain) "Low" else (if (maxProb > 0.80) "High" else "Moderate"),
                    isRejected = isRejected
                )

                withContext(Dispatchers.Main) { _diseaseResult.value = initialResult }

                if (!isRejected && !isUncertain && maxProb >= 0.70f && BuildConfig.HF_TOKEN.isNotEmpty()) {
                    runHFIntelligenceCycle(displayDiseaseName, cropType, languageCode)
                } else {
                    withContext(Dispatchers.Main) { _isLoading.value = false }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _isLoading.value = false; _error.value = "Scanning failed: ${e.message}" }
            }
        }
    }

    private fun runHFIntelligenceCycle(diseaseName: String, cropType: String, languageCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            attemptHFEnhancement(diseaseName, cropType, languageCode)
            withContext(Dispatchers.Main) { _isLoading.value = false }
        }
    }

    private suspend fun attemptHFEnhancement(diseaseName: String, cropType: String, languageCode: String): Boolean {
        return try {
            val languageName = if (languageCode == "hi") "Hindi" else "English"
            
            // Refined prompt to be more token-efficient and explicit about length
            val prompt = """
                Plant Pathology Expert: Crop $cropType, Disease $diseaseName.
                Response Language: $languageName.
                Return ONLY a JSON object with:
                "hindi_name": "Name in Hindi",
                "description": "2-sentence summary",
                "symptoms": ["list", "of", "3"],
                "prevention": ["list", "of", "3"],
                "treatment": [{"name": "Organic/Chemical", "steps": ["step1", "step2"]}]
                Max 3 treatment objects. Keep it brief.
            """.trimIndent()

            // Increased maxTokens to 1500 to prevent truncation (especially important for Hindi UTF-8)
            val request = HFChatRequest(
                model = "meta-llama/Llama-3.1-8B-Instruct", 
                messages = listOf(HFMessage("user", prompt)),
                maxTokens = 1500 
            )
            val response = RetrofitClient.hfApi.chatCompletion("Bearer ${BuildConfig.HF_TOKEN}", request).awaitResponse()

            if (response.isSuccessful) {
                val rawText = response.body()?.choices?.get(0)?.message?.content?.trim() ?: ""
                Log.d(TAG, "HF Response: $rawText")
                
                val firstBrace = rawText.indexOf("{")
                val lastBrace = rawText.lastIndexOf("}")
                
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    val jsonText = rawText.substring(firstBrace, lastBrace + 1)
                    
                    // Basic check: Ensure root object is likely complete
                    if (!jsonText.endsWith("}")) {
                        Log.e(TAG, "Extracted JSON is incomplete")
                        return false
                    }

                    try {
                        val json = JSONObject(jsonText)
                        withContext(Dispatchers.Main) {
                            val current = _diseaseResult.value
                            if (current != null) {
                                _diseaseResult.value = current.copy(
                                    hindi_name = json.optString("hindi_name", current.hindi_name),
                                    description = "<font color='#1A237E'>" + json.optString("description", current.description) + "</font>",
                                    symptoms = formatJsonArrayToHtml(json, "symptoms", "#E65100"), 
                                    prevention = formatJsonArrayToHtml(json, "prevention", "#2E7D32"), 
                                    treatment = formatTreatmentToHtml(json)
                                )
                            }
                        }
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON Parsing failed: ${e.message}")
                        return false
                    }
                } else {
                    Log.e(TAG, "No JSON object found in response")
                    return false
                }
            } else {
                Log.e(TAG, "HF API Error: ${response.code()}")
                return false
            }
        } catch (e: Exception) { 
            Log.e(TAG, "HF Enhancement Exception: ${e.message}")
            false
        }
    }

    private fun formatJsonArrayToHtml(json: JSONObject, key: String, color: String): String {
        val array = json.optJSONArray(key) ?: return "<i>Data updating...</i>"
        val sb = StringBuilder()
        for (i in 0 until array.length()) {
            val item = array.optString(i)
            if (item.isNotEmpty()) {
                sb.append("<font color='$color'>• ").append(item).append("</font><br>")
            }
        }
        return sb.toString()
    }

    private fun formatTreatmentToHtml(json: JSONObject): String {
        val array = json.optJSONArray("treatment") ?: return "<i>Info temporarily unavailable</i>"
        val sb = StringBuilder()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name", "Method")
            sb.append("<b>").append(name).append("</b><br>")
            val steps = obj.optJSONArray("steps")
            if (steps != null) {
                for (j in 0 until steps.length()) {
                    val step = steps.optString(j)
                    if (step.isNotEmpty()) {
                        sb.append(" - ").append(step).append("<br>")
                    }
                }
            }
            sb.append("<br>")
        }
        return sb.toString()
    }

    fun clearError() { _error.value = null }
    fun clearToast() { _toastEvent.value = null }
}

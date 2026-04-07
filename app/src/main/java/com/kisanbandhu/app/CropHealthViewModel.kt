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

                val imageProcessor = ImageProcessor.Builder()
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
                
                // Retry loading if DB was empty
                if (localDiseaseDb == null) {
                    val json = getApplication<Application>().assets.open("disease_info.json").bufferedReader().use { it.readText() }
                    localDiseaseDb = JSONObject(json)
                }

                // If uncertain, we don't want to show info for the "guessed" disease
                val localInfo = if (isUncertain) {
                    null
                } else {
                    localDiseaseDb?.optJSONObject(detectedLabel)
                }
                
                // Fix: Replace \n with <br> for HTML rendering to avoid "one line" output
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
            
            val prompt = """
                You are a Senior Plant Pathologist. Crop: $cropType, Disease: $diseaseName.
                Provide all information in $languageName.
                Provide a JSON object with these EXACT keys:
                "hindi_name": (Name in Hindi regardless of requested language),
                "description": (Brief 2-sentence summary in $languageName),
                "symptoms": (JSON array of 3 strings in $languageName),
                "prevention": (JSON array of 3 strings in $languageName),
                "treatment": (JSON array of objects in $languageName [{"name": "🌿 Organic Control", "steps": ["..."]}, {"name": "⚗️ Chemical Treatment", "steps": ["..."]}])
                Return ONLY JSON.
            """.trimIndent()

            val request = HFChatRequest("meta-llama/Meta-Llama-3-8B-Instruct", listOf(HFMessage("user", prompt)))
            val response = RetrofitClient.hfApi.chatCompletion("Bearer ${BuildConfig.HF_TOKEN}", request).awaitResponse()

            if (response.isSuccessful) {
                var jsonText = response.body()?.choices?.get(0)?.message?.content?.trim() ?: ""
                if (jsonText.contains("{")) jsonText = jsonText.substring(jsonText.indexOf("{"), jsonText.lastIndexOf("}") + 1)
                val json = JSONObject(jsonText)
                
                withContext(Dispatchers.Main) {
                    val current = _diseaseResult.value
                    if (current != null) {
                        _diseaseResult.value = current.copy(
                            hindi_name = json.optString("hindi_name", current.hindi_name),
                            description = "<font color='#1A237E'>" + json.optString("description", current.description) + "</font>",
                            symptoms = formatJsonArrayToHtml(json, "symptoms", "#E65100"), // Warning Orange
                            prevention = formatJsonArrayToHtml(json, "prevention", "#2E7D32"), // Proactive Green
                            treatment = formatTreatmentToHtml(json)
                        )
                    }
                }
                true
            } else {
                withContext(Dispatchers.Main) {
                    val msg = if (languageCode == "hi") "एआई एन्हांसमेंट अनुपलब्ध है। स्थानीय डेटा का उपयोग किया जा रहा है।" else "AI Enhancement unavailable. Using local data."
                    _toastEvent.value = msg
                }
                false
            }
        } catch (e: Exception) { 
            withContext(Dispatchers.Main) {
                val msg = if (languageCode == "hi") "कनेक्शन त्रुटि। एआई एन्हांसमेंट छोड़ दिया गया।" else "Connection error. AI Enhancement skipped."
                _toastEvent.value = msg
            }
            false 
        }
    }

    private fun formatJsonArrayToHtml(json: JSONObject, key: String, color: String): String {
        val array = json.optJSONArray(key) ?: return json.optString(key, "")
        val sb = StringBuilder()
        for (i in 0 until array.length()) {
            if (i > 0) sb.append("<br><br>")
            sb.append("<font color='$color'>• ").append(array.getString(i)).append("</font>")
        }
        return sb.toString()
    }

    private fun formatTreatmentToHtml(json: JSONObject): String {
        val array = json.optJSONArray("treatment") ?: return json.optString("treatment", "")
        val sb = StringBuilder()
        for (i in 0 until array.length()) {
            val item = array.get(i)
            if (item is JSONObject) {
                val name = item.optString("name")
                val isOrganic = name.contains("Organic", true) || name.contains("जैविक", true)
                val color = if (isOrganic) "#1B5E20" else "#C62828" // Dark Green vs Potent Red
                
                if (sb.isNotEmpty()) sb.append("<br><br>")
                sb.append("<b><font color='$color'>$name</font></b>")
                
                val steps = item.optJSONArray("steps")
                if (steps != null) {
                    for (j in 0 until steps.length()) {
                        sb.append("<br><font color='$color'>• ").append(steps.getString(j)).append("</font>")
                    }
                }
            }
        }
        return sb.toString()
    }

    fun clearError() { _error.value = null }
    fun clearToast() { _toastEvent.value = null }

    override fun onCleared() { super.onCleared(); interpreter?.close() }
}

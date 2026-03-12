package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MarketAnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MarketViewModelDebug"
    private val apiKey = "579b464db66ec23bdd000001afaa734f77284bf5426a771984ced436"

    private val _allProcessedMarketData = MutableLiveData<List<MarketPriceInfo>>()
    val allProcessedMarketData: LiveData<List<MarketPriceInfo>> = _allProcessedMarketData

    private val _recommendedCropPrices = MutableLiveData<Map<String, MarketPriceInfo?>>()
    val recommendedCropPrices: LiveData<Map<String, MarketPriceInfo?>> = _recommendedCropPrices

    private val _marketStats = MutableLiveData<MarketStats>()
    val marketStats: LiveData<MarketStats> = _marketStats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _infoMessage = MutableLiveData<String?>()
    val infoMessage: LiveData<String?> = _infoMessage

    // Core Categorization Lists
    private val vegetableKeywords = listOf("Tomato", "Potato", "Onion", "Cabbage", "Carrot", "Brinjal", "Cauliflower", "Ginger", "Garlic", "Bhindi", "Capsicum", "Peas", "Radish")
    private val fruitKeywords = listOf("Mango", "Banana", "Papaya", "Grapes", "Apple", "Orange", "Pomegranate", "Watermelon", "Muskmelon", "Guava", "Lemon", "Pineapple")
    private val grainKeywords = listOf("Rice", "Wheat", "Maize", "Barley", "Millet", "Jowar", "Bajra", "Ragi", "Gram", "Arhar", "Tur", "Moong", "Urad", "Cotton", "Soyabean")

    fun fetchMarketIntelligence(state: String? = null) {
        _isLoading.value = true
        _infoMessage.value = null
        
        // Layer 1: Fetch state-specific data
        RetrofitClient.marketApi.getMarketPrices(apiKey = apiKey, state = state, limit = 100)
            .enqueue(object : Callback<MarketResponse> {
                override fun onResponse(call: Call<MarketResponse>, response: Response<MarketResponse>) {
                    val records = response.body()?.records ?: emptyList()
                    if (records.size < 10) {
                        _infoMessage.postValue("No recent mandi prices found in your region. Showing national market data.")
                        fetchNationalData(records)
                    } else {
                        processMarketData(records)
                    }
                }
                override fun onFailure(call: Call<MarketResponse>, t: Throwable) {
                    fetchNationalData(emptyList())
                }
            })
    }

    private fun fetchNationalData(stateRecords: List<MarketRecord>) {
        RetrofitClient.marketApi.getMarketPrices(apiKey = apiKey, limit = 100)
            .enqueue(object : Callback<MarketResponse> {
                override fun onResponse(call: Call<MarketResponse>, response: Response<MarketResponse>) {
                    _isLoading.value = false
                    val nationalRecords = response.body()?.records ?: emptyList()
                    if (nationalRecords.isEmpty() && stateRecords.isEmpty()) {
                        processMarketData(getStaticBackupData())
                    } else {
                        val combined = (stateRecords + nationalRecords).distinctBy { it.commodity + it.market }
                        processMarketData(combined)
                    }
                }
                override fun onFailure(call: Call<MarketResponse>, t: Throwable) {
                    _isLoading.value = false
                    processMarketData(getStaticBackupData())
                }
            })
    }

    // NEW: Improved Search Logic with Fallback & Real-time fetching
    fun searchMarketPrices(query: String) {
        if (query.isEmpty()) return
        
        _isLoading.value = true
        
        // Search Layer 1: Specific Commodity Search
        RetrofitClient.marketApi.getMarketPrices(apiKey = apiKey, commodity = query, limit = 20)
            .enqueue(object : Callback<MarketResponse> {
                override fun onResponse(call: Call<MarketResponse>, response: Response<MarketResponse>) {
                    _isLoading.value = false
                    val records = response.body()?.records ?: emptyList()
                    
                    if (records.isNotEmpty()) {
                        processMarketData(records)
                    } else {
                        // Search Layer 2: Filter existing data
                        val filtered = _allProcessedMarketData.value?.filter { 
                            it.record.commodity?.contains(query, ignoreCase = true) == true 
                        } ?: emptyList()
                        
                        if (filtered.isNotEmpty()) {
                            _allProcessedMarketData.value = filtered
                        } else {
                            // Search Layer 3: Static Match
                            val staticMatch = getStaticBackupData().filter { 
                                it.commodity?.contains(query, ignoreCase = true) == true 
                            }
                            if (staticMatch.isNotEmpty()) {
                                processMarketData(staticMatch)
                            } else {
                                _error.value = "No market results for '$query'"
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<MarketResponse>, t: Throwable) {
                    _isLoading.value = false
                    _error.value = "Search failed: ${t.message}"
                }
            })
    }

    private fun processMarketData(records: List<MarketRecord>) {
        val processedList = records.mapNotNull { record ->
            val min = record.minPrice?.toDoubleOrNull() ?: return@mapNotNull null
            val max = record.maxPrice?.toDoubleOrNull() ?: return@mapNotNull null
            val modal = record.modalPrice?.toDoubleOrNull() ?: return@mapNotNull null
            val commodity = record.commodity ?: "Unknown"

            val mid = (max + min) / 2
            val priceTag = if (modal >= mid) "Good Price" else "Low Price"
            val profit = if (min > 0) ((modal - min) / min) * 100 else 0.0
            
            MarketPriceInfo(
                record = record,
                priceTag = priceTag,
                trendPercentage = profit,
                isRising = profit >= 0,
                category = getCategoryForCommodity(commodity)
            )
        }

        val finalProcessedList = ensureAllCategoriesRepresented(processedList)
        _allProcessedMarketData.postValue(finalProcessedList)
        calculateStats(finalProcessedList)
    }

    private fun ensureAllCategoriesRepresented(data: List<MarketPriceInfo>): List<MarketPriceInfo> {
        val result = data.toMutableList()
        val staticData = getStaticBackupData()
        listOf("Vegetables", "Fruits", "Grains").forEach { cat ->
            if (result.none { it.category == cat }) {
                val itemsForCat = staticData.filter { getCategoryForCommodity(it.commodity ?: "") == cat }
                result.addAll(itemsForCat.map { convertToPriceInfo(it) })
            }
        }
        return result
    }

    private fun getCategoryForCommodity(commodity: String): String {
        return when {
            vegetableKeywords.any { commodity.contains(it, ignoreCase = true) } -> "Vegetables"
            fruitKeywords.any { commodity.contains(it, ignoreCase = true) } -> "Fruits"
            grainKeywords.any { commodity.contains(it, ignoreCase = true) } -> "Grains"
            else -> "Other"
        }
    }

    private fun convertToPriceInfo(record: MarketRecord): MarketPriceInfo {
        val min = record.minPrice?.toDoubleOrNull() ?: 0.0
        val modal = record.modalPrice?.toDoubleOrNull() ?: 0.0
        val profit = if (min > 0) ((modal - min) / min) * 100 else 0.0
        return MarketPriceInfo(
            record = record,
            priceTag = "Average",
            trendPercentage = profit,
            isRising = true,
            category = getCategoryForCommodity(record.commodity ?: "")
        )
    }

    private fun calculateStats(data: List<MarketPriceInfo>) {
        val rising = data.count { it.trendPercentage > 0 }
        val falling = data.count { it.trendPercentage < 0 }
        val bestDeals = data.count { it.priceTag == "Good Price" }
        _marketStats.postValue(MarketStats(rising, falling, bestDeals))
    }

    fun fetchPricesForRecommendedCrops(crops: List<String>) {
        val currentData = _allProcessedMarketData.value ?: emptyList()
        val results = mutableMapOf<String, MarketPriceInfo?>()
        crops.forEach { cropName ->
            val match = currentData.find { it.record.commodity?.contains(cropName, ignoreCase = true) == true }
            if (match != null) {
                results[cropName] = match
            } else {
                val staticMatch = getStaticBackupData().find { it.commodity?.contains(cropName, ignoreCase = true) == true }
                results[cropName] = staticMatch?.let { convertToPriceInfo(it) }
            }
        }
        _recommendedCropPrices.postValue(results)
    }

    private fun getStaticBackupData(): List<MarketRecord> {
        return listOf(
            MarketRecord("National", "Market", "Benchmark", "Wheat", "Local", "2024", "2100", "2500", "2300"),
            MarketRecord("National", "Market", "Benchmark", "Rice", "Fine", "2024", "2000", "2400", "2200"),
            MarketRecord("National", "Market", "Benchmark", "Maize", "Medium", "2024", "1900", "2300", "2100"),
            MarketRecord("National", "Market", "Benchmark", "Tomato", "Hybrid", "2024", "1500", "2100", "1800"),
            MarketRecord("National", "Market", "Benchmark", "Onion", "Red", "2024", "1800", "2200", "2000"),
            MarketRecord("National", "Market", "Benchmark", "Potato", "Jyoti", "2024", "1200", "1600", "1400"),
            MarketRecord("National", "Market", "Benchmark", "Mango", "Common", "2024", "7000", "9000", "8000"),
            MarketRecord("National", "Market", "Benchmark", "Grapes", "Common", "2024", "4000", "6000", "5000"),
            MarketRecord("National", "Market", "Benchmark", "Apple", "Royal", "2024", "5000", "7000", "6000"),
            MarketRecord("National", "Market", "Benchmark", "Banana", "Common", "2024", "1500", "2500", "2000")
        )
    }
}

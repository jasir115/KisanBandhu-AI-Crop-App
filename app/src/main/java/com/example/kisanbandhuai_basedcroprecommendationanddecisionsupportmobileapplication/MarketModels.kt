package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import com.google.gson.annotations.SerializedName

data class MarketResponse(
    @SerializedName("records")
    val records: List<MarketRecord> = emptyList(),
    @SerializedName("total")
    val total: Int = 0
)

data class MarketRecord(
    @SerializedName("state")
    val state: String?,
    @SerializedName("district")
    val district: String?,
    @SerializedName("market")
    val market: String?,
    @SerializedName("commodity")
    val commodity: String?,
    @SerializedName("variety")
    val variety: String?,
    @SerializedName("arrival_date")
    val arrivalDate: String?,
    @SerializedName("min_price")
    val minPrice: String?,
    @SerializedName("max_price")
    val maxPrice: String?,
    @SerializedName("modal_price")
    val modalPrice: String?
)

data class MarketPriceInfo(
    val record: MarketRecord,
    val priceTag: String, // Good Price, Average, Low Price
    val trendPercentage: Double,
    val isRising: Boolean,
    val category: String // Vegetables, Fruits, Grains, Other
)

data class MarketStats(
    val risingCount: Int,
    val fallingCount: Int,
    val bestDealCount: Int
)

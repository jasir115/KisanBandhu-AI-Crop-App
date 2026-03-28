package com.kisanbandhu.app

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val location: Location,
    val current: Current,
    val forecast: Forecast
)

data class Location(
    val name: String,
    val region: String,
    val country: String
)

data class Current(
    @SerializedName("temp_c") val tempC: Double,
    val condition: Condition,
    val humidity: Int,
    @SerializedName("wind_kph") val windKph: Double,
    @SerializedName("vis_km") val visKm: Double,
    @SerializedName("precip_mm") val precipMm: Double
)

data class Condition(
    val text: String,
    val icon: String
)

data class Forecast(
    @SerializedName("forecastday") val forecastDay: List<ForecastDay>
)

data class ForecastDay(
    val date: String,
    val day: Day
)

data class Day(
    @SerializedName("maxtemp_c") val maxTempC: Double,
    @SerializedName("mintemp_c") val minTempC: Double,
    @SerializedName("totalprecip_mm") val totalPrecipMm: Double,
    val condition: Condition
)
package com.moblized.newsfeed.data

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.roundToInt

data class Place(val name: String, val region: String, val latitude: Double, val longitude: Double) {
    val label: String get() = if (region.isBlank()) name else "$name, $region"

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("region", region).put("lat", latitude).put("lon", longitude)

    companion object {
        fun fromJson(o: JSONObject) = Place(o.optString("name"), o.optString("region"), o.optDouble("lat"), o.optDouble("lon"))
    }
}

data class WeatherReport(
    val place: Place,
    val fahrenheit: Boolean,
    val temperature: Int,
    val feelsLike: Int?,
    val condition: String,
    val windSpeed: Int,
    val windDirection: String,
    val humidity: Int,
    val high: Int,
    val low: Int,
    val dayCondition: String,
    val rainChance: Int?,
    val maxWind: Int,
    val fetchedAt: Long,
) {
    val unit: String get() = if (fahrenheit) "°F" else "°C"
    val windUnit: String get() = if (fahrenheit) "mph" else "km/h"
}

/** Free forecasts from Open-Meteo (no key needed). */
object WeatherService {
    const val SOURCE_NAME = "Open-Meteo"
    const val SOURCE_URL = "https://open-meteo.com/"

    fun searchPlaces(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(String(Http.get(url), Charsets.UTF_8))
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val region = listOf(r.optString("admin1"), r.optString("country_code").ifBlank { r.optString("country") })
                .filter { it.isNotBlank() }
                .joinToString(", ")
            Place(r.optString("name"), region, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    fun forecast(place: Place, fahrenheit: Boolean): WeatherReport {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
            "&timezone=auto&forecast_days=1" +
            (if (fahrenheit) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")
        val json = JSONObject(String(Http.get(url), Charsets.UTF_8))
        val current = json.optJSONObject("current") ?: throw IOException("No current weather")
        val daily = json.optJSONObject("daily") ?: throw IOException("No forecast")

        fun dailyValue(key: String): Double? = daily.optJSONArray(key)?.let { arr ->
            if (arr.length() == 0 || arr.isNull(0)) null else arr.optDouble(0)
        }

        val dayCode = dailyValue("weather_code")?.toInt() ?: current.optInt("weather_code")
        return WeatherReport(
            place = place,
            fahrenheit = fahrenheit,
            temperature = current.optDouble("temperature_2m").takeIf { !it.isNaN() }?.roundToInt()
                ?: throw IOException("No temperature"),
            feelsLike = current.optDouble("apparent_temperature").takeIf { !it.isNaN() }?.roundToInt(),
            condition = describe(current.optInt("weather_code")),
            windSpeed = current.optDouble("wind_speed_10m").takeIf { !it.isNaN() }?.roundToInt() ?: 0,
            windDirection = compass(current.optDouble("wind_direction_10m")),
            humidity = current.optDouble("relative_humidity_2m").takeIf { !it.isNaN() }?.roundToInt() ?: 0,
            high = dailyValue("temperature_2m_max")?.roundToInt() ?: 0,
            low = dailyValue("temperature_2m_min")?.roundToInt() ?: 0,
            dayCondition = describe(dayCode),
            rainChance = dailyValue("precipitation_probability_max")?.takeIf { !it.isNaN() }?.roundToInt(),
            maxWind = dailyValue("wind_speed_10m_max")?.takeIf { !it.isNaN() }?.roundToInt() ?: 0,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun compass(degrees: Double): String {
        if (degrees.isNaN()) return ""
        val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(((degrees % 360) + 360) % 360 / 45.0).roundToInt() % 8]
    }

    /** WMO weather interpretation codes. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Rain showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorms"
        96, 99 -> "Thunderstorms with hail"
        else -> "Unknown"
    }

    fun emoji(condition: String): String = when {
        condition.startsWith("Clear") || condition.startsWith("Mostly clear") -> "☀️"
        condition.startsWith("Partly") -> "🌤️"
        condition.startsWith("Overcast") -> "☁️"
        condition.startsWith("Fog") -> "🌫️"
        condition.contains("Thunder") -> "⛈️"
        condition.contains("snow", ignoreCase = true) -> "🌨️"
        condition.contains("rain", ignoreCase = true) || condition.contains("Drizzle") || condition.contains("showers") -> "🌧️"
        else -> "🌡️"
    }
}

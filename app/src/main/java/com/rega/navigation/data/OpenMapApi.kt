package com.rega.navigation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Keyless development adapter using OpenStreetMap Nominatim for user-triggered search
 * and the public OSRM demo endpoint for car routing.
 *
 * IMPORTANT: public endpoints are best-effort demonstration services, not production
 * infrastructure. Nominatim is limited to <= 1 request/second across the whole app,
 * does not permit autocomplete, and requires attribution. OSRM demo asks clients not
 * to exceed 1 request/second and offers no uptime/latency guarantee.
 */
data class PlaceResult(val title: String, val address: String, val latitude: Double, val longitude: Double)
data class RouteStep(
    val instruction: String,
    val roadName: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val maneuver: String
)
data class RouteResult(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<RouteStep>
)

class OpenMapApi {
    fun isConfigured(): Boolean = true

    suspend fun search(term: String, lat: Double, lon: Double, languageTag: String = "fa"): List<PlaceResult> = withContext(Dispatchers.IO) {
        require(term.isNotBlank()) { "عبارت جست‌وجو خالی است" }
        requireValidCoordinates(lat, lon)
        val normalized = term.trim().lowercase(Locale.ROOT)
        searchCache["$languageTag:$normalized"]?.let { return@withContext it }

        // Nominatim public service policy: no autocomplete and max 1 request/sec.
        val waitMs = synchronized(rateLock) {
            val now = System.currentTimeMillis()
            val wait = (lastSearchAt + 1_100L - now).coerceAtLeast(0L)
            lastSearchAt = now + wait
            wait
        }
        if (waitMs > 0) delay(waitMs)

        val q = URLEncoder.encode(term.trim(), "UTF-8")
        // Keep the search scoped to Iran while biasing results toward the current location.
        val viewbox = "${lon - 1.0},${lat + 1.0},${lon + 1.0},${lat - 1.0}"
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&countrycodes=ir&viewbox=$viewbox&bounded=0&q=$q"
        val json = getArray(url, acceptLanguage(languageTag))
        val results = buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val latitude = item.optString("lat").toDoubleOrNull() ?: continue
                val longitude = item.optString("lon").toDoubleOrNull() ?: continue
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
                val display = item.optString("display_name", term)
                val title = item.optJSONObject("namedetails")?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?: display.substringBefore(",").ifBlank { term }
                add(PlaceResult(title, display, latitude, longitude))
            }
        }
        searchCache["$languageTag:$normalized"] = results
        results
    }

    suspend fun route(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        languageTag: String = "fa"
    ): RouteResult = withContext(Dispatchers.IO) {
        requireValidCoordinates(fromLat, fromLon)
        requireValidCoordinates(toLat, toLon)
        // The shared OSRM demo asks clients to stay at or below one request per second.
        val routeWaitMs = synchronized(routeRateLock) {
            val now = System.currentTimeMillis()
            val wait = (lastRouteAt + 1_100L - now).coerceAtLeast(0L)
            lastRouteAt = now + wait
            wait
        }
        if (routeWaitMs > 0) delay(routeWaitMs)
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson&steps=true&alternatives=true"
        val json = getObject(url, acceptLanguage(languageTag))
        if (json.optString("code") != "Ok") {
            val message = json.optString("message").ifBlank { "برای این مبدأ و مقصد مسیری پیدا نشد" }
            error(message)
        }
        val routes = json.optJSONArray("routes") ?: error("سرویس مسیریابی مسیری برنگرداند")
        val selected = routes.optJSONObject(0) ?: error("مسیر قابل استفاده‌ای پیدا نشد")
        val geometry = selected.optJSONObject("geometry")?.optJSONArray("coordinates")
            ?: error("هندسه مسیر در پاسخ سرویس ناقص است")
        val points = buildList {
            for (i in 0 until geometry.length()) {
                val pair = geometry.optJSONArray(i) ?: continue
                if (pair.length() < 2) continue
                val longitude = pair.optDouble(0, Double.NaN)
                val latitude = pair.optDouble(1, Double.NaN)
                if (latitude.isFinite() && longitude.isFinite()) add(latitude to longitude)
            }
        }
        if (points.size < 2) error("مختصات مسیر معتبر نیست")

        val steps = buildList {
            val legs = selected.optJSONArray("legs")
            if (legs != null) for (legIndex in 0 until legs.length()) {
                val leg = legs.optJSONObject(legIndex) ?: continue
                val stepArray = leg.optJSONArray("steps") ?: continue
                for (stepIndex in 0 until stepArray.length()) {
                    val step = stepArray.optJSONObject(stepIndex) ?: continue
                    val maneuver = step.optJSONObject("maneuver") ?: JSONObject()
                    val type = maneuver.optString("type", "continue")
                    val modifier = maneuver.optString("modifier", "")
                    val road = step.optString("name", "").trim()
                    val instruction = instructionFor(type, modifier, road, languageTag)
                    add(RouteStep(
                        instruction = instruction,
                        roadName = road,
                        distanceMeters = step.optDouble("distance", 0.0).toInt(),
                        durationSeconds = step.optDouble("duration", 0.0).toInt(),
                        maneuver = type
                    ))
                }
            }
        }
        RouteResult(
            distanceMeters = selected.optDouble("distance", 0.0).toInt(),
            durationSeconds = selected.optDouble("duration", 0.0).toInt(),
            encodedPolyline = encodePolyline(points),
            steps = steps
        )
    }

    private fun instructionFor(type: String, modifier: String, road: String, language: String): String {
        val roadPart = road.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val phrase = when (language) {
            "en" -> when (type) {
                "depart" -> "Start"
                "arrive" -> "You have arrived"
                "turn" -> "Turn ${modifier.ifBlank { "ahead" }}"
                "new name", "continue" -> "Continue"
                "merge" -> "Merge ${modifier.ifBlank { "ahead" }}"
                "on ramp" -> "Take the ramp"
                "off ramp" -> "Take the exit"
                "roundabout", "rotary" -> "Enter the roundabout"
                "fork" -> "Keep ${modifier.ifBlank { "ahead" }} at the fork"
                else -> "Continue"
            }
            "tr" -> when (type) {
                "depart" -> "Yola çıkın"
                "arrive" -> "Hedefe ulaştınız"
                "turn" -> "${modifier.ifBlank { "ileriye" }} dönün"
                "roundabout", "rotary" -> "Göbekten dönün"
                "off ramp" -> "Çıkışı kullanın"
                "merge" -> "Yola katılın"
                else -> "Yol boyunca devam edin"
            }
            "ar" -> when (type) {
                "depart" -> "ابدأ السير"
                "arrive" -> "لقد وصلت إلى وجهتك"
                "turn" -> "انعطف ${arabicModifier(modifier)}"
                "roundabout", "rotary" -> "ادخل الدوار"
                "off ramp" -> "اسلك المخرج"
                "merge" -> "اندمج مع الطريق"
                else -> "تابع السير"
            }
            "ckb" -> when (type) {
                "depart" -> "بە ڕێ بکەوە"
                "arrive" -> "گەیشتیتە مەبەست"
                "turn" -> "سوڕانەوە ${soraniModifier(modifier)}"
                "roundabout", "rotary" -> "بچۆ ناو بازنەڕێگا"
                "off ramp" -> "دەرچوونەکە بەکاربهێنە"
                else -> "بەردەوام بە لەسەر ڕێگا"
            }
            "kmr" -> when (type) {
                "depart" -> "Bi rê bikeve"
                "arrive" -> "Gihîştî armanca xwe"
                "turn" -> "Bizîvire ${modifier.ifBlank { "pêşve" }}"
                "roundabout", "rotary" -> "Bikeve çemberê"
                "off ramp" -> "Derketinê bi kar bîne"
                else -> "Li ser rê bidomîne"
            }
            else -> when (type) {
                "depart" -> "حرکت کنید"
                "arrive" -> "به مقصد رسیدید"
                "turn" -> "به ${persianModifier(modifier)} بپیچید"
                "roundabout", "rotary" -> "وارد میدان شوید"
                "off ramp" -> "از خروجی خارج شوید"
                "on ramp" -> "وارد مسیر ورودی شوید"
                "merge" -> "به مسیر بپیوندید"
                "fork" -> "در دوراهی ${persianModifier(modifier)} را انتخاب کنید"
                else -> "به مسیر ادامه دهید"
            }
        }
        return phrase + roadPart
    }

    private fun persianModifier(value: String) = when (value) {
        "left", "sharp left", "slight left" -> "چپ"
        "right", "sharp right", "slight right" -> "راست"
        "uturn" -> "دوربرگردان"
        else -> "مسیر اصلی"
    }
    private fun arabicModifier(value: String) = when (value) {
        "left", "sharp left", "slight left" -> "يساراً"
        "right", "sharp right", "slight right" -> "يميناً"
        else -> "للأمام"
    }
    private fun soraniModifier(value: String) = when (value) {
        "left", "sharp left", "slight left" -> "بۆ چەپ"
        "right", "sharp right", "slight right" -> "بۆ ڕاست"
        else -> "بۆ پێشەوە"
    }

    private fun acceptLanguage(tag: String): String = when (tag) {
        "fa", "ckb" -> "fa,en;q=0.8"
        "kmr" -> "tr,en;q=0.8"
        "ar" -> "ar,en;q=0.8"
        "tr" -> "tr,en;q=0.8"
        else -> "en,fa;q=0.8"
    }

    private fun getArray(address: String, acceptLanguage: String): JSONArray {
        val body = request(address, acceptLanguage)
        return JSONArray(body)
    }

    private fun getObject(address: String, acceptLanguage: String): JSONObject {
        val body = request(address, acceptLanguage)
        return JSONObject(body)
    }

    private fun request(address: String, acceptLanguage: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("User-Agent", "REGA-Android/1.0.0 (OpenStreetMap navigation prototype)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", acceptLanguage)
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("خطای سرویس نقشه ($code)")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun requireValidCoordinates(lat: Double, lon: Double) {
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
            "مختصات جغرافیایی معتبر نیست"
        }
    }

    private fun encodePolyline(points: List<Pair<Double, Double>>): String {
        val output = StringBuilder()
        var lastLat = 0
        var lastLon = 0
        for ((latitude, longitude) in points) {
            val lat = (latitude * 1e5).toInt()
            val lon = (longitude * 1e5).toInt()
            encodeValue(lat - lastLat, output)
            encodeValue(lon - lastLon, output)
            lastLat = lat
            lastLon = lon
        }
        return output.toString()
    }

    private fun encodeValue(delta: Int, output: StringBuilder) {
        var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (value >= 0x20) {
            output.append(((0x20 or (value and 0x1f)) + 63).toChar())
            value = value shr 5
        }
        output.append((value + 63).toChar())
    }

    companion object {
        private val searchCache = ConcurrentHashMap<String, List<PlaceResult>>()
        private val rateLock = Any()
        private var lastSearchAt = 0L
        private val routeRateLock = Any()
        private var lastRouteAt = 0L
    }
}

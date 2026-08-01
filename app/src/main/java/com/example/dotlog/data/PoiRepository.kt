package com.example.dotlog.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "PoiRepository"
private const val USER_AGENT =
    "Dotlog Location Tracker/1.0 (https://github.com/baraaml/dotlog; contact: baraalearnsml@gmail.com)"

class PoiRepository(private val api: OverpassApi) {
    
    // In-memory cache to save quota and provide instant feedback for repeated clicks
    private val cache = mutableMapOf<Pair<Double, Double>, String>()

    suspend fun resolvePlaceName(lat: Double, lon: Double): String {
        // Round to 5 decimal places (~1.1m precision) for stable cache keys
        val roundedLat = Math.round(lat * 100000.0) / 100000.0
        val roundedLon = Math.round(lon * 100000.0) / 100000.0
        val cacheKey = roundedLat to roundedLon
        
        cache[cacheKey]?.let {
            Log.d(TAG, "Cache hit for $roundedLat,$roundedLon: $it")
            return it
        }

        val radii = listOf(200, 500, 1000)

        for (radius in radii) {
            try {
                Log.d(TAG, "Resolving POI at $lat,$lon with radius $radius...")
                val query = """
                    [out:json][timeout:15];
                    (
                      node(around:$radius,$lat,$lon)[name];
                      way(around:$radius,$lat,$lon)[name];
                    );
                    out tags center;
                """.trimIndent()

                val response = api.query(query)
                val elementsWithNames = response.elements.filter { !it.tags?.get("name").isNullOrBlank() }

                if (elementsWithNames.isNotEmpty()) {
                    val name = elementsWithNames.first().tags!!["name"] ?: "Unnamed area"
                    Log.d(TAG, "Successfully resolved: $name")
                    cache[cacheKey] = name
                    return name
                }
            } catch (e: Exception) {
                Log.e(TAG, "Overpass API error at radius $radius: ${e.message}")
                
                // CRITICAL: If we are being rate-limited (HTTP 429), stop the loop immediately.
                // Continuing to the next radius will just fail and might extend the ban.
                if (e.message?.contains("429") == true) {
                    Log.e(TAG, "Rate limit hit. Aborting resolution loop.")
                    break
                }
                
                // For other errors (timeouts, 504), we try the next radius
            }
        }

        Log.w(TAG, "Fallback to 'Unnamed area' after checking all radii.")
        return "Unnamed area"
    }

    companion object {
        fun create(): PoiRepository {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(OverpassApi.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return PoiRepository(retrofit.create(OverpassApi::class.java))
        }
    }
}

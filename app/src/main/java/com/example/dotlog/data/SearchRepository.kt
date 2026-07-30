package com.example.dotlog.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val USER_AGENT =
    "Dotlog Location Tracker/1.0 (https://github.com/baraaml/dotlog; contact: baraalearnsml@gmail.com)"

data class SearchResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String
)

class SearchRepository(private val api: GeocodingApi) {

    private var lastCallTime = 0L

    suspend fun search(query: String): List<SearchResult> {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCallTime
        if (elapsed < 1_000) {
            kotlinx.coroutines.delay(1_000 - elapsed)
        }
        lastCallTime = System.currentTimeMillis()

        return try {
            val response = api.search(query)
            response.mapNotNull { result ->
                val lat = result.lat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = result.lon.toDoubleOrNull() ?: return@mapNotNull null
                SearchResult(
                    displayName = result.display_name,
                    latitude = lat,
                    longitude = lon,
                    type = result.type
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun create(): SearchRepository {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://nominatim.openstreetmap.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return SearchRepository(retrofit.create(GeocodingApi::class.java))
        }
    }
}

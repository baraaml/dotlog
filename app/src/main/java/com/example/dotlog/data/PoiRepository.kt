package com.example.dotlog.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val USER_AGENT =
    "Dotlog Location Tracker/1.0 (https://github.com/baraaml/dotlog; contact: baraalearnsml@gmail.com)"

class PoiRepository(private val api: OverpassApi) {

    suspend fun resolvePlaceName(lat: Double, lon: Double): String {
        val radii = listOf(200, 500, 1000)

        for (radius in radii) {
            try {
                val query = """
                    [out:json];
                    (
                      node(around:$radius,$lat,$lon)[name];
                      way(around:$radius,$lat,$lon)[name];
                    );
                    out tags center;
                """.trimIndent()

                val response = api.query(query)
                val elementsWithNames = response.elements.filter { !it.tags?.get("name").isNullOrBlank() }

                if (elementsWithNames.isNotEmpty()) {
                    return elementsWithNames.first().tags!!["name"] ?: "Unnamed area"
                }
            } catch (e: Exception) {
                // API error — continue to next radius or fallback below
            }
        }

        return "Unnamed area"
    }

    companion object {
        fun create(): PoiRepository {
            val client = OkHttpClient.Builder()
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

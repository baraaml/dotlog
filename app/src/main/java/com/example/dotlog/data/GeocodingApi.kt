package com.example.dotlog.data

import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {
    @GET("search.php")
    suspend fun search(@Query("q") query: String, @Query("format") format: String = "json", @Query("limit") limit: Int = 5): List<GeocodingResult>
}

data class GeocodingResult(
    val display_name: String,
    val lat: String,
    val lon: String,
    val type: String,
    val category: String?
)

package com.example.dotlog.data

import retrofit2.http.GET
import retrofit2.http.Query

interface OverpassApi {
    @GET("api/interpreter")
    suspend fun query(@Query("data") data: String): OverpassResponse

    companion object {
        const val BASE_URL = "https://overpass-api.de/"
    }
}

data class OverpassResponse(
    val elements: List<OverpassElement>
)

data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double?,
    val lon: Double?,
    val tags: Map<String, String>?
)

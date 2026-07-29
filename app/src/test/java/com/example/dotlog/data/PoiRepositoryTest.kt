package com.example.dotlog.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PoiRepositoryTest {

    @Test
    fun `test resolvePlaceName widens radius`() = runBlocking {
        val fakeApi = FakeOverpassApi()
        val repository = PoiRepository(fakeApi)

        // Case 1: Found at 200m
        fakeApi.resultAtRadius = mapOf(200 to "Cafe 200")
        assertEquals("Cafe 200", repository.resolvePlaceName(0.0, 0.0))

        // Case 2: Not at 200m, found at 500m
        fakeApi.resultAtRadius = mapOf(500 to "Park 500")
        assertEquals("Park 500", repository.resolvePlaceName(0.0, 0.0))

        // Case 3: Not at 200m or 500m, found at 1km
        fakeApi.resultAtRadius = mapOf(1000 to "Mall 1000")
        assertEquals("Mall 1000", repository.resolvePlaceName(0.0, 0.0))

        // Case 4: Not found at all
        fakeApi.resultAtRadius = emptyMap()
        assertEquals("Unnamed area", repository.resolvePlaceName(0.0, 0.0))
    }

    class FakeOverpassApi : OverpassApi {
        var resultAtRadius = mapOf<Int, String>()

        override suspend fun query(data: String): OverpassResponse {
            // Extract radius from query string "around:RADIUS,lat,lon"
            val radiusStr = Regex("around:(\\d+)").find(data)?.groupValues?.get(1) ?: "0"
            val radius = radiusStr.toInt()
            
            val name = resultAtRadius[radius]
            val elements = if (name != null) {
                listOf(OverpassElement("node", 1L, 0.0, 0.0, mapOf("name" to name)))
            } else {
                emptyList()
            }
            return OverpassResponse(elements)
        }
    }
}

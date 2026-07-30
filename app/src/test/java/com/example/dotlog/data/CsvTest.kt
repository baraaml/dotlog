package com.example.dotlog.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `parse simple fields`() {
        assertEquals(listOf("a", "b", "c"), parseCsvLine("a,b,c"))
    }

    @Test
    fun `parse quoted field with comma`() {
        assertEquals(listOf("Cafe, Cairo", "30.0"), parseCsvLine("\"Cafe, Cairo\",30.0"))
    }

    @Test
    fun `parse quoted field with embedded quote`() {
        assertEquals(listOf("8\" pizza", "NYC"), parseCsvLine("\"8\"\" pizza\",NYC"))
    }

    @Test
    fun `parse csv header`() {
        val parts = parseCsvLine("latitude,longitude,placeName,timestamp")
        assertEquals(4, parts.size)
        assertEquals("latitude", parts[0])
        assertEquals("timestamp", parts[3])
    }

    @Test
    fun `parse real export line with name containing comma`() {
        val line = "30.0444,31.2357,\"Cairo, Egypt\",1700000000000"
        val parts = parseCsvLine(line)
        assertEquals(4, parts.size)
        assertEquals("30.0444", parts[0])
        assertEquals("31.2357", parts[1])
        assertEquals("Cairo, Egypt", parts[2])
        assertEquals("1700000000000", parts[3])
    }

    @Test
    fun `parse line with empty field`() {
        assertEquals(listOf("a", "", "c"), parseCsvLine("a,,c"))
    }
}

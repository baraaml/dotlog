package com.example.dotlog.data

fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i += 2
                continue
            }
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> {
                result.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
        i++
    }
    result.add(current.toString())
    return result
}

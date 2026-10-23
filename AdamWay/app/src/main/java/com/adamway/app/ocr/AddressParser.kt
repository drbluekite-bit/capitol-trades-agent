package com.adamway.app.ocr

/**
 * Turns a block of raw OCR text (often several lines — house number, street,
 * town, postcode, each on its own line on an envelope or door plaque) into a
 * single free-text address line, the same shape as typing one address into
 * Google Maps' own search bar.
 */
object AddressParser {

    // e.g. "SW1A 1AA", "EC1A1BB", "W1A0AX" — UK postcode formats.
    private val UK_POSTCODE_REGEX = Regex(
        "\\b([Gg][Ii][Rr] 0[Aa]{2})|" +
            "((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|" +
            "([A-Za-z][A-Ha-hJ-Yj-y][0-9][A-Za-z]?))))\\s?[0-9][A-Za-z]{2})\\b"
    )

    fun parse(rawText: String): String {
        val joined = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        if (joined.isEmpty()) return ""

        val match = UK_POSTCODE_REGEX.find(joined) ?: return joined
        val normalized = match.value.uppercase().replace(Regex("\\s+"), " ").trim()
        return joined.replaceRange(match.range, normalized)
    }
}

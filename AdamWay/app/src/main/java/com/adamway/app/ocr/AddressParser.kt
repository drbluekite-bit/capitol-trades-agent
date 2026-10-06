package com.adamway.app.ocr

/** Best-effort split of a block of OCR text into address form fields for the user to correct. */
object AddressParser {

    // e.g. "SW1A 1AA", "EC1A1BB", "W1A0AX" — UK postcode formats.
    private val UK_POSTCODE_REGEX = Regex(
        "\\b([Gg][Ii][Rr] 0[Aa]{2})|" +
            "((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|" +
            "([A-Za-z][A-Ha-hJ-Yj-y][0-9][A-Za-z]?))))\\s?[0-9][A-Za-z]{2})\\b"
    )

    private val HOUSE_NUMBER_REGEX = Regex("^\\d+[A-Za-z]?$")

    data class ParsedAddress(
        val houseNumber: String = "",
        val houseName: String = "",
        val postcode: String = "",
        val rawText: String = "",
    )

    fun parse(rawText: String): ParsedAddress {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val postcodeMatch = UK_POSTCODE_REGEX.find(rawText)
        val postcode = postcodeMatch?.value?.uppercase()?.replace(Regex("\\s+"), " ")?.trim() ?: ""

        val remainingLines = lines.filterNot { line ->
            postcode.isNotEmpty() && line.uppercase().contains(postcode.replace(" ", ""))
        }

        var houseNumber = ""
        var houseName = ""
        for (line in remainingLines) {
            val firstToken = line.split(" ", ",").firstOrNull { it.isNotBlank() } ?: continue
            if (houseNumber.isEmpty() && HOUSE_NUMBER_REGEX.matches(firstToken)) {
                houseNumber = firstToken
                val rest = line.removePrefix(firstToken).trim(',', ' ')
                if (rest.isNotEmpty() && houseName.isEmpty()) houseName = rest
            } else if (houseName.isEmpty()) {
                houseName = line
            }
        }

        return ParsedAddress(
            houseNumber = houseNumber,
            houseName = houseName,
            postcode = postcode,
            rawText = rawText,
        )
    }
}

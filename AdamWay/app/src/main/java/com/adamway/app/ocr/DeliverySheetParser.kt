package com.adamway.app.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

/** One delivery extracted from a photographed delivery sheet, before review. */
data class ParsedDelivery(
    val number: Int?,
    val addressText: String,
    val notes: String,
)

/**
 * Turns one photo of a printed, two-deliveries-per-page delivery sheet into
 * up to two [ParsedDelivery] rows.
 *
 * This is built around one specific, consistent template — not a generic
 * table reader — and only works because of that: the delivery number is
 * printed noticeably larger than everything around it in the top-left of
 * each half; the address is whatever paragraph contains a UK postcode;
 * "Delivery Instructions" and "CDA Notes" are literal, consistently
 * printed section labels. None of that would hold up against an arbitrary
 * or handwritten sheet. Nothing here is trusted blindly either way —
 * every row this produces is meant to be reviewed and corrected before it
 * touches the real queue.
 */
object DeliverySheetParser {

    private val UK_POSTCODE_REGEX = Regex(
        "\\b([Gg][Ii][Rr] 0[Aa]{2})|" +
            "((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|" +
            "([A-Za-z][A-Ha-hJ-Yj-y][0-9][A-Za-z]?))))\\s?[0-9][A-Za-z]{2})\\b"
    )
    private val BARE_NUMBER_REGEX = Regex("^\\d{2,4}$")

    private data class OcrLine(val text: String, val box: Rect)

    fun parse(result: Text): List<ParsedDelivery> {
        val lines = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line -> line.boundingBox?.let { OcrLine(line.text, it) } }
        if (lines.isEmpty()) return emptyList()

        // Each delivery block starts with an "Order No" row — using that
        // (rather than, say, splitting the photo in half) copes with the
        // page not being perfectly centred in frame, and naturally excludes
        // the shared page header above the first block.
        val orderNoTops = lines
            .filter { it.text.contains("Order No", ignoreCase = true) }
            .map { it.box.top }
            .sorted()
        if (orderNoTops.isEmpty()) return emptyList()

        val boundaries = orderNoTops + Int.MAX_VALUE
        return (0 until boundaries.size - 1).mapNotNull { i ->
            val blockLines = lines.filter { it.box.top >= boundaries[i] && it.box.top < boundaries[i + 1] }
            parseBlock(blockLines)
        }
    }

    private fun parseBlock(blockLines: List<OcrLine>): ParsedDelivery? {
        if (blockLines.isEmpty()) return null

        // The address is whichever paragraph contains the postcode line —
        // identified by column (left edge close to the postcode line's),
        // not by a fixed line count, since not every address has the same
        // number of lines.
        val postcodeLine = blockLines.firstOrNull { UK_POSTCODE_REGEX.containsMatchIn(it.text) }
        val addressText = postcodeLine?.let { pc ->
            val blockLeft = blockLines.minOf { it.box.left }
            val blockRight = blockLines.maxOf { it.box.right }
            val tolerance = ((blockRight - blockLeft) * 0.08).toInt().coerceAtLeast(20)
            val column = blockLines
                .filter { abs(it.box.left - pc.box.left) <= tolerance }
                .sortedBy { it.box.top }
            column.take(column.indexOf(pc) + 1).takeLast(5).joinToString(", ") { it.text.trim() }
        }.orEmpty()

        // The delivery number sits in the same corner as the two time
        // fields but, unlike them, is bare digits with no colon — and is
        // printed in a visibly larger font than anything else nearby, so
        // among bare-digit candidates the tallest line wins.
        val number = blockLines
            .filter { BARE_NUMBER_REGEX.matches(it.text.trim()) }
            .maxByOrNull { it.box.height() }
            ?.text?.trim()?.toIntOrNull()

        val notes = extractNotes(blockLines)

        if (addressText.isBlank() && number == null) return null
        return ParsedDelivery(number = number, addressText = addressText, notes = notes)
    }

    /** Combines the "Delivery Instructions" and "CDA Notes" boxes into one notes string. */
    private fun extractNotes(blockLines: List<OcrLine>): String {
        val instrLabel = blockLines.firstOrNull { it.text.contains("Delivery Instructions", ignoreCase = true) }
        val cdaLabel = blockLines.firstOrNull { it.text.contains("CDA Notes", ignoreCase = true) }
        if (instrLabel == null && cdaLabel == null) return ""

        val customerLabel = blockLines.firstOrNull { it.text.trim().startsWith("Customer:", ignoreCase = true) }
        val sectionBottom = customerLabel?.box?.top ?: (blockLines.maxOfOrNull { it.box.bottom } ?: Int.MAX_VALUE)
        val left = blockLines.minOf { it.box.left }
        val right = blockLines.maxOf { it.box.right }
        val midX = (left + right) / 2

        fun collect(label: OcrLine?, isLeftSide: Boolean): String {
            if (label == null) return ""
            return blockLines
                .asSequence()
                .filter { it !== label }
                .filter { it.box.top >= label.box.top && it.box.top < sectionBottom }
                .filter { if (isLeftSide) it.box.left < midX else it.box.left >= midX }
                .filter { !it.text.contains("CDA Notes", ignoreCase = true) }
                .filter { !it.text.contains("Delivery Instructions", ignoreCase = true) }
                .sortedBy { it.box.top }
                .joinToString(" ") { it.text.trim() }
                .trim()
        }

        val instructions = collect(instrLabel, isLeftSide = true)
        val cda = collect(cdaLabel, isLeftSide = false)
        return listOf(instructions, cda).filter { it.isNotBlank() }.joinToString(" / ")
    }
}

package com.adamway.app.journey

import com.adamway.app.data.Address

/** A single Google Maps launch: a contiguous slice of the full queue. */
data class JourneyWindow(
    val stops: List<Address>,
    val startIndex: Int,
    val endIndex: Int,
    /** True while there are still queued addresses beyond [endIndex] to slide in later. */
    val hasMoreToAppend: Boolean,
) {
    val isFinalStop: Boolean get() = startIndex == endIndex
}

/**
 * Works around Google Maps' ~10-stop limit by only ever sending it up to
 * [legSize] stops at a time, then sliding that window forward by one stop
 * each time the driver confirms they've left the stop they were just at —
 * so a queue of up to 18 addresses can be driven as one continuous journey.
 */
class JourneyManager(private val legSize: Int = 5) {

    fun beginJourney(queue: List<Address>): JourneyWindow? {
        if (queue.isEmpty()) return null
        val end = minOf(legSize, queue.size) - 1
        return JourneyWindow(
            stops = queue.subList(0, end + 1),
            startIndex = 0,
            endIndex = end,
            hasMoreToAppend = end < queue.size - 1,
        )
    }

    /**
     * Called after the driver leaves [currentWindow]'s first stop. [addedNewStop]
     * tells the caller whether a new address was slid in and Maps needs
     * relaunching with the updated stop list, or whether Maps already has
     * everything it needs and can be left running on its own.
     */
    data class AdvanceResult(val window: JourneyWindow, val addedNewStop: Boolean)

    fun advance(queue: List<Address>, currentWindow: JourneyWindow): AdvanceResult? {
        if (currentWindow.startIndex >= queue.size - 1) return null // already at/after the last stop
        val newStart = currentWindow.startIndex + 1
        val newEnd = minOf(currentWindow.endIndex + (if (currentWindow.hasMoreToAppend) 1 else 0), queue.size - 1)
        val window = JourneyWindow(
            stops = queue.subList(newStart, newEnd + 1),
            startIndex = newStart,
            endIndex = newEnd,
            hasMoreToAppend = newEnd < queue.size - 1,
        )
        return AdvanceResult(window, addedNewStop = currentWindow.hasMoreToAppend)
    }
}

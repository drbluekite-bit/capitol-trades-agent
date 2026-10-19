package com.velodromeghost.core.race

import kotlin.math.max

/**
 * The omnium: four races in one session (scratch, tempo, elimination, points race).
 * The first three pay 40 points to the winner, 38 to second, 36 to third and so on; in the final
 * points race the points each rider scores (sprints and lap gains/losses) are added to their total.
 * The ghosts keep their identity (and colour) across the four races.
 */
object Omnium {
    data class Event(val mode: RaceMode, val laps: Int)

    data class EventResult(
        val mode: RaceMode,
        val finished: Boolean,
        /** Competitor ids, best first (0 = you). */
        val placings: List<Int>,
        /** Omnium points scored in this event, by competitor id. */
        val points: Map<Int, Int>,
        val names: Map<Int, String>,
    ) {
        val riderPoints: Int get() = points[0] ?: 0
        val riderPlace: Int? get() = placings.indexOf(0).takeIf { it >= 0 }?.plus(1)
        val winnerId: Int? get() = placings.firstOrNull()
        val winner: Competitor? get() = winnerId?.let { if (it == 0) Competitor.RIDER else Competitor.GHOST }

        /** Points of the best ghost in this event. */
        val ghostPoints: Int get() = points.filterKeys { it != 0 }.values.maxOrNull() ?: 0
    }

    /** 40 for 1st, 38 for 2nd ... never less than 1. */
    fun placePoints(place: Int): Int = max(1, 42 - 2 * place)

    fun events(full: Boolean): List<Event> = if (full) {
        listOf(Event(RaceMode.SCRATCH, 40), Event(RaceMode.TEMPO, 40), Event(RaceMode.ELIMINATION, 0), Event(RaceMode.POINTS_RACE, 80))
    } else {
        listOf(Event(RaceMode.SCRATCH, 20), Event(RaceMode.TEMPO, 20), Event(RaceMode.ELIMINATION, 0), Event(RaceMode.POINTS_RACE, 40))
    }

    fun score(state: RaceState): EventResult {
        val finished = state.phase == RacePhase.FINISHED
        val ordered = state.standings.sortedBy { it.place }
        val placings = ordered.map { it.id }
        val points = if (state.mode == RaceMode.POINTS_RACE) {
            ordered.associate { it.id to it.points }
        } else {
            placings.withIndex().associate { (i, id) -> id to placePoints(i + 1) }
        }.toMutableMap()
        // Abandoning a race scores nothing.
        if (!finished) points[0] = 0
        return EventResult(state.mode, finished, placings, points, ordered.associate { it.id to it.name })
    }

    /** Totals by competitor id. */
    fun totals(results: List<EventResult>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (r in results) r.points.forEach { (id, p) -> out[id] = (out[id] ?: 0) + p }
        return out
    }

    /** Overall order (id to total), best first. Ties go to whoever placed better in the points race. */
    fun standings(results: List<EventResult>): List<Pair<Int, Int>> {
        val totals = totals(results)
        val last = results.lastOrNull()?.placings.orEmpty()
        return totals.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }
                .thenBy { last.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } })
            .map { it.key to it.value }
    }

    fun riderTotal(results: List<EventResult>) = totals(results)[0] ?: 0

    /** Best ghost's total. */
    fun ghostTotal(results: List<EventResult>) = totals(results).filterKeys { it != 0 }.values.maxOrNull() ?: 0

    fun riderPlace(results: List<EventResult>) = standings(results).indexOfFirst { it.first == 0 } + 1

    fun overallWinner(results: List<EventResult>): Competitor =
        if (standings(results).firstOrNull()?.first == 0) Competitor.RIDER else Competitor.GHOST

    fun names(results: List<EventResult>): Map<Int, String> = results.flatMap { it.names.entries }.associate { it.key to it.value }
}

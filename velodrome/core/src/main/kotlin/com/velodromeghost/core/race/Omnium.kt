package com.velodromeghost.core.race

/**
 * The omnium: four races in one session (scratch, tempo, elimination, points race).
 * The first three pay 40 points to the winner and 38 to second; in the final points race
 * the points you score are added to (and lap gains/losses change) your running total.
 */
object Omnium {
    data class Event(val mode: RaceMode, val laps: Int)

    data class EventResult(
        val mode: RaceMode,
        val winner: Competitor?,
        val riderPoints: Int,
        val ghostPoints: Int,
        val finished: Boolean,
    )

    const val WINNER_POINTS = 40
    const val SECOND_POINTS = 38

    fun events(full: Boolean): List<Event> = if (full) {
        listOf(Event(RaceMode.SCRATCH, 40), Event(RaceMode.TEMPO, 40), Event(RaceMode.ELIMINATION, 2), Event(RaceMode.POINTS_RACE, 80))
    } else {
        listOf(Event(RaceMode.SCRATCH, 20), Event(RaceMode.TEMPO, 20), Event(RaceMode.ELIMINATION, 2), Event(RaceMode.POINTS_RACE, 40))
    }

    fun score(state: RaceState): EventResult {
        val finished = state.phase == RacePhase.FINISHED
        if (state.mode == RaceMode.POINTS_RACE) {
            return EventResult(state.mode, state.winner, state.riderTotalPoints - state.ghostLapPoints, state.ghostTotalPoints - state.riderLapPoints, finished)
        }
        val winner = state.winner.takeIf { finished }
        return EventResult(
            mode = state.mode,
            winner = winner,
            riderPoints = when (winner) { Competitor.RIDER -> WINNER_POINTS; Competitor.GHOST -> SECOND_POINTS; null -> 0 },
            ghostPoints = when (winner) { Competitor.GHOST -> WINNER_POINTS; Competitor.RIDER -> SECOND_POINTS; null -> WINNER_POINTS },
            finished = finished,
        )
    }

    fun riderTotal(results: List<EventResult>) = results.sumOf { it.riderPoints }
    fun ghostTotal(results: List<EventResult>) = results.sumOf { it.ghostPoints }

    /** Overall winner; a tie goes to whoever won the points race (the last event). */
    fun overallWinner(results: List<EventResult>): Competitor {
        val r = riderTotal(results)
        val g = ghostTotal(results)
        return when {
            r > g -> Competitor.RIDER
            g > r -> Competitor.GHOST
            else -> results.lastOrNull()?.winner ?: Competitor.GHOST
        }
    }
}

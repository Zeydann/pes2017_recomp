package com.pes2017.recomp

import kotlin.math.max

/**
 * The pad's compound sequences, one at a time, in the order they were asked for.
 *
 * A compound swipe is a handful of held states a beat apart (see [SwipeSequence]).
 * Scheduling each one on its own timer meant a second flick cancelled whatever the
 * first was still doing -- the tail of one sequence and the head of the next
 * interleaved on the d-pad, and whichever arrived last won. Nothing about that is
 * recoverable at the title's end: it just reads two tactics where the player asked
 * for one.
 *
 * So the sequences are queued instead of raced. A new action starts after the
 * previous one has finished plus a gap, every step keeps its dwell, and the order
 * the player made the flicks in is the order the title sees them. The queue is a
 * plain FIFO of absolute due times, so the overlay needs one timer rather than one
 * per step, and the whole thing can be run forward in a test by moving `nowMs`.
 *
 * Free of Android types on purpose ([MacroSequencerTest]).
 */
class MacroSequencer(
    /** The beat left between one sequence's last step and the next's first. */
    private val gapMs: Long = GAP_MS,
) {

    private class Entry(val dueMs: Long, val step: SwipeStep)

    private val queue = ArrayList<Entry>(8)

    /** When the next sequence may begin: after the last one ends, plus [gapMs]. */
    private var cursorMs = Long.MIN_VALUE

    /** Queues one bound action, returning its first step's due time, or null. */
    fun enqueue(action: SwipeAction, nowMs: Long): Long? {
        val steps = SwipeSequence.steps(action)
        if (steps.isEmpty()) return null
        // Never in the past, and never before the sequence already queued has
        // finished: that is what keeps rapid flicks from colliding.
        val start = max(nowMs, cursorMs)
        var end = start
        var first: Long? = null
        for (step in steps) {
            val due = start + step.delayMs
            queue.add(Entry(due, step))
            if (first == null) first = due
            end = max(end, due)
        }
        cursorMs = end + gapMs
        return first
    }

    /** When the earliest queued step is due, or null when the queue is empty. */
    fun nextDueMs(): Long? = if (queue.isEmpty()) null else queue[0].dueMs

    /**
     * Every step due at or before [nowMs], oldest first, removed from the queue.
     * Steps are whole held states, so a caller that only pushes the last of them
     * still ends up where the queue says it should.
     */
    fun takeDue(nowMs: Long): List<SwipeStep> {
        var count = 0
        while (count < queue.size && queue[count].dueMs <= nowMs) count++
        if (count == 0) return emptyList()
        val due = ArrayList<SwipeStep>(count)
        for (index in 0 until count) due.add(queue[index].step)
        queue.subList(0, count).clear()
        return due
    }

    val busy: Boolean get() = queue.isNotEmpty()

    /** How many steps are still waiting: for the tests, and for a last-resort log. */
    val pending: Int get() = queue.size

    /** Drops everything queued: a pause, a reset, the editor, a stance flip. */
    fun clear() {
        queue.clear()
        cursorMs = Long.MIN_VALUE
    }

    companion object {
        /**
         * The gap between sequences. Long enough for the title to see the clear
         * step at the end of one, short enough that two flicks in a row still
         * feel like two flicks and not a delay.
         */
        const val GAP_MS = 50L
    }
}

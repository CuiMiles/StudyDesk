package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.vocabulary.*
import org.junit.Assert.*
import org.junit.Test

class VocabularyEngineTest {

    @Test
    fun testQueueBuildingAndLimit() {
        val store = VocabularyStore(
            settings = UserVocabularySettings(dailyNewLimit = 3)
        )
        val words = listOf("w1", "w2", "w3", "w4", "w5")
        val sessionStore = VocabularyEngine.buildQueue(store, words, "2026-09-15")
        val queue = sessionStore.session?.queue ?: emptyList()

        assertEquals(3, queue.size)
        assertEquals(AttemptMode.NEW, queue[0].mode)
        assertEquals("w1", queue[0].entryId)
    }

    @Test
    fun testReviewIntervalsProgression() {
        val today = "2026-09-15"
        var store = VocabularyStore()
        store = VocabularyEngine.buildQueue(store, listOf("w1"), today)

        // Stage -1 -> 0 (interval 1 day: due 2026-09-16)
        val att1 = store.session!!.queue.first().id
        store = VocabularyEngine.rate(store, att1, Rating.KNOWN, today)
        assertEquals(0, store.progress["w1"]?.stage)
        assertEquals("2026-09-16", store.progress["w1"]?.due)
        assertEquals(ProgressState.REVIEW, store.progress["w1"]?.state)

        // Simulate next day review
        val day2 = "2026-09-16"
        store = VocabularyEngine.buildQueue(store, listOf("w1"), day2)
        val att2 = store.session!!.queue.first().id
        store = VocabularyEngine.rate(store, att2, Rating.KNOWN, day2)
        // Stage 0 -> 1 (interval 3 days: due 2026-09-19)
        assertEquals(1, store.progress["w1"]?.stage)
        assertEquals("2026-09-19", store.progress["w1"]?.due)

        // Simulate review at day 2026-09-19
        val day3 = "2026-09-19"
        store = VocabularyEngine.buildQueue(store, listOf("w1"), day3)
        val att3 = store.session!!.queue.first().id
        store = VocabularyEngine.rate(store, att3, Rating.KNOWN, day3)
        // Stage 1 -> 2 (interval 7 days: due 2026-09-26)
        assertEquals(2, store.progress["w1"]?.stage)
        assertEquals("2026-09-26", store.progress["w1"]?.due)
    }

    @Test
    fun testFuzzyFeedbackDoesNotAdvanceStage() {
        val today = "2026-09-15"
        var store = VocabularyStore(
            progress = mapOf("w1" to UserProgress(state = ProgressState.REVIEW, stage = 2, due = today))
        )
        store = VocabularyEngine.buildQueue(store, listOf("w1"), today)
        val att = store.session!!.queue.first().id
        store = VocabularyEngine.rate(store, att, Rating.FUZZY, today)

        // Stage remains 2, state becomes LEARNING, due tomorrow
        assertEquals(2, store.progress["w1"]?.stage)
        assertEquals("2026-09-16", store.progress["w1"]?.due)
        assertEquals(ProgressState.LEARNING, store.progress["w1"]?.state)
    }

    @Test
    fun testUnknownFeedbackResetsAndQueuesRetry() {
        val today = "2026-09-15"
        var store = VocabularyStore(
            progress = mapOf("w1" to UserProgress(state = ProgressState.REVIEW, stage = 3, due = today))
        )
        // Add padding words to test retry interleaving
        val words = listOf("w1", "w2", "w3", "w4")
        store = VocabularyEngine.buildQueue(store, words, today)
        assertEquals(4, store.session!!.queue.size)

        val att1 = store.session!!.queue.first().id
        store = VocabularyEngine.rate(store, att1, Rating.UNKNOWN, today)

        // Stage resets to -1, due tomorrow
        assertEquals(-1, store.progress["w1"]?.stage)
        assertEquals("2026-09-16", store.progress["w1"]?.due)

        // Retry queued at index min(3, queue.size)
        val retryAttempt = store.session!!.queue.find { it.entryId == "w1" && it.mode == AttemptMode.RETRY }
        assertNotNull("Retry attempt must be queued", retryAttempt)

        // Answering KNOWN on same-day retry must NOT advance stage beyond learning
        val attRetry = retryAttempt!!.id
        // Move retry attempt to top for testing
        val reorderedSession = store.session!!.copy(
            queue = listOf(retryAttempt) + store.session!!.queue.filter { it.id != attRetry }
        )
        store = store.copy(session = reorderedSession)
        store = VocabularyEngine.rate(store, attRetry, Rating.KNOWN, today)
        assertEquals("Retry success on failed word must not advance to review", ProgressState.LEARNING, store.progress["w1"]?.state)
        assertEquals(-1, store.progress["w1"]?.stage)
    }

    @Test
    fun testMarkFamiliarAndRelearn() {
        val today = "2026-09-15"
        var store = VocabularyStore()
        store = VocabularyEngine.buildQueue(store, listOf("w1", "w2"), today)

        store = VocabularyEngine.markFamiliar(store, "w1")
        assertEquals(ProgressState.FAMILIAR, store.progress["w1"]?.state)
        assertNull(store.progress["w1"]?.due)
        assertTrue("Familiar word must be removed from session queue", store.session!!.queue.none { it.entryId == "w1" })

        // Relearning puts it back into learning for today
        store = VocabularyEngine.relearn(store, "w1", today)
        assertEquals(ProgressState.LEARNING, store.progress["w1"]?.state)
        assertEquals(today, store.progress["w1"]?.due)
    }

    @Test
    fun testSpellingCheck() {
        assertTrue(VocabularyEngine.checkSpelling("mitigate", "mitigate"))
        assertTrue(VocabularyEngine.checkSpelling("  Mitigate ", "mitigate"))
        assertTrue(VocabularyEngine.checkSpelling("co-operate", "cooperate", listOf("co-operate")))
        assertFalse(VocabularyEngine.checkSpelling("mitgite", "mitigate"))
    }
}

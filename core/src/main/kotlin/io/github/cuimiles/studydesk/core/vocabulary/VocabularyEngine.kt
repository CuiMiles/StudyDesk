package io.github.cuimiles.studydesk.core.vocabulary

import io.github.cuimiles.studydesk.core.calendar.addDays
import java.util.UUID
import kotlin.math.min

object VocabularyEngine {

    val INTERVALS = listOf(1, 3, 7, 14, 30, 60)

    fun getDayStats(store: VocabularyStore, day: String): DailyStats {
        return store.daily[day] ?: DailyStats()
    }

    fun buildQueue(
        store: VocabularyStore,
        allEntryIds: List<String>,
        today: String
    ): VocabularyStore {
        val oldSession = if (store.session?.day == today) {
            store.session
        } else {
            LearningSession(day = today, queue = emptyList(), retries = emptyMap(), failed = emptyList())
        }

        val todayStats = getDayStats(store, today)
        val availableSet = allEntryIds.toSet()

        // Due reviews
        val dueEntries = allEntryIds.filter { id ->
            val p = store.progress[id]
            p != null && p.state != ProgressState.FAMILIAR && p.due != null && p.due <= today
        }.sortedWith(compareBy<String> { store.progress[it]?.due ?: "" }.thenBy { it })

        val remainingNewLimit = (store.settings.dailyNewLimit - todayStats.newIds.size).coerceAtLeast(0)

        val eligibleNewSet = allEntryIds.filter { id ->
            store.progress[id] == null && !todayStats.newIds.contains(id)
        }.toSet()

        var newCount = 0
        val pendingFromOld = oldSession.queue.filter { a ->
            if (a.mode == AttemptMode.NEW) {
                if (!eligibleNewSet.contains(a.entryId) || newCount >= remainingNewLimit) {
                    false
                } else {
                    newCount++
                    true
                }
            } else {
                val p = store.progress[a.entryId]
                availableSet.contains(a.entryId) && p != null && p.state != ProgressState.FAMILIAR &&
                        (a.mode == AttemptMode.RETRY || (p.due != null && p.due <= today))
            }
        }

        val seenInPending = pendingFromOld.map { it.entryId }.toSet()
        val combined = mutableListOf<Attempt>()

        // 1. Due reviews not yet in pending
        for (id in dueEntries) {
            if (!seenInPending.contains(id)) {
                combined.add(Attempt(UUID.randomUUID().toString(), id, AttemptMode.REVIEW))
            }
        }

        // 2. Pending queue from earlier session
        combined.addAll(pendingFromOld)

        val seenOverall = combined.map { it.entryId }.toMutableSet()

        // 3. Add fresh new words up to limit
        for (id in allEntryIds) {
            if (newCount < remainingNewLimit && eligibleNewSet.contains(id) && !seenOverall.contains(id)) {
                combined.add(Attempt(UUID.randomUUID().toString(), id, AttemptMode.NEW))
                seenOverall.add(id)
                newCount++
            }
        }

        val newSession = oldSession.copy(day = today, queue = combined)
        return store.copy(session = newSession)
    }

    fun rate(
        store: VocabularyStore,
        attemptId: String,
        rating: Rating,
        today: String
    ): VocabularyStore {
        val session = store.session ?: throw IllegalStateException("没有正在进行的学习会话")
        if (session.day != today) {
            throw IllegalStateException("日期已变化，请重新进入学习")
        }
        if (session.queue.isEmpty() || session.queue.first().id != attemptId) {
            throw IllegalStateException("这次回答已处理或次序不符，请刷新词卡")
        }
        if (store.events.any { it.id == attemptId }) {
            throw IllegalStateException("这次回答已记录，请勿重复提交")
        }

        val attempt = session.queue.first()
        val wordId = attempt.entryId
        if (store.progress[wordId]?.state == ProgressState.FAMILIAR) {
            throw IllegalStateException("该词已加入熟词本")
        }

        val newQueue = session.queue.drop(1).toMutableList()
        val dayStats = getDayStats(store, today)

        val newIds = dayStats.newIds.toMutableList()
        val reviewIds = dayStats.reviewIds.toMutableList()

        if (attempt.mode == AttemptMode.NEW && !newIds.contains(wordId)) {
            newIds.add(wordId)
        }
        if (attempt.mode != AttemptMode.NEW && !reviewIds.contains(wordId)) {
            reviewIds.add(wordId)
        }

        val updatedStats = dayStats.copy(
            newIds = newIds,
            reviewIds = reviewIds,
            attempts = dayStats.attempts + 1
        )

        val oldProgress = store.progress[wordId] ?: UserProgress(
            state = ProgressState.LEARNING,
            stage = -1,
            first = today
        )

        val newProgress: UserProgress
        val newRetries = session.retries.toMutableMap()
        val newFailed = session.failed.toMutableList()
        val failedBefore = session.failed.contains(wordId)

        when (rating) {
            Rating.UNKNOWN -> {
                newProgress = oldProgress.copy(
                    stage = -1,
                    state = ProgressState.LEARNING,
                    due = addDays(today, 1),
                    last = today
                )
                if (!failedBefore) {
                    newFailed.add(wordId)
                }
                val retryCount = newRetries[wordId] ?: 0
                if (retryCount < 2) {
                    newRetries[wordId] = retryCount + 1
                    val insertIdx = min(3, newQueue.size)
                    newQueue.add(insertIdx, Attempt(UUID.randomUUID().toString(), wordId, AttemptMode.RETRY))
                }
            }
            Rating.FUZZY -> {
                newProgress = oldProgress.copy(
                    state = ProgressState.LEARNING,
                    due = addDays(today, 1),
                    last = today
                )
            }
            Rating.KNOWN -> {
                if (failedBefore) {
                    // Previously failed today; does not advance stage!
                    newProgress = oldProgress.copy(
                        state = ProgressState.LEARNING,
                        due = addDays(today, 1),
                        last = today
                    )
                } else {
                    val nextStage = min(5, oldProgress.stage + 1)
                    newProgress = oldProgress.copy(
                        stage = nextStage,
                        state = ProgressState.REVIEW,
                        due = addDays(today, INTERVALS[nextStage].toLong()),
                        last = today
                    )
                }
            }
        }

        val newProgressMap = store.progress + (wordId to newProgress)
        val event = ReviewEvent(
            id = attempt.id,
            entryId = wordId,
            day = today,
            rating = rating,
            mode = attempt.mode
        )
        val newEvents = (store.events + event).takeLast(1000)

        val updatedDaily = store.daily + (today to updatedStats)
        val updatedSession = session.copy(
            queue = newQueue,
            retries = newRetries,
            failed = newFailed
        )

        return store.copy(
            revision = store.revision + 1,
            progress = newProgressMap,
            daily = updatedDaily,
            events = newEvents,
            session = updatedSession
        )
    }

    fun markFamiliar(store: VocabularyStore, wordId: String): VocabularyStore {
        val oldP = store.progress[wordId]
        val newP = (oldP ?: UserProgress(stage = -1)).copy(
            state = ProgressState.FAMILIAR,
            due = null
        )
        val newProgressMap = store.progress + (wordId to newP)
        val newSession = store.session?.copy(
            queue = store.session.queue.filter { it.entryId != wordId }
        )
        return store.copy(
            revision = store.revision + 1,
            progress = newProgressMap,
            session = newSession,
            settings = store.settings.copy(familiarHintSeen = true)
        )
    }

    fun relearn(store: VocabularyStore, wordId: String, today: String): VocabularyStore {
        val oldP = store.progress[wordId] ?: UserProgress()
        val newP = oldP.copy(
            state = ProgressState.LEARNING,
            stage = -1,
            due = today
        )
        val newProgressMap = store.progress + (wordId to newP)
        val newSession = store.session?.copy(
            queue = store.session.queue.filter { it.entryId != wordId },
            failed = store.session.failed.filter { it != wordId },
            retries = store.session.retries - wordId
        )
        return store.copy(
            revision = store.revision + 1,
            progress = newProgressMap,
            session = newSession
        )
    }

    fun toggleFavorite(store: VocabularyStore, wordId: String): VocabularyStore {
        val newFavs = if (store.favorites.contains(wordId)) {
            store.favorites - wordId
        } else {
            store.favorites + wordId
        }
        return store.copy(
            revision = store.revision + 1,
            favorites = newFavs
        )
    }

    fun checkSpelling(input: String, headword: String, variants: List<String> = emptyList()): Boolean {
        fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
        val normalizedInput = norm(input)
        val targets = listOf(headword) + variants
        return targets.any { norm(it) == normalizedInput }
    }
}

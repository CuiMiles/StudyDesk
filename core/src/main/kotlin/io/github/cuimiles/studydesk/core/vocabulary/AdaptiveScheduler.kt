package io.github.cuimiles.studydesk.core.vocabulary

import io.github.cuimiles.studydesk.core.calendar.addDays
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Explainable spaced-repetition heuristic, calibrated per learner, not a universal forgetting formula. */
object AdaptiveScheduler {
    fun next(old: UserProgress, rating: Rating, day: String, settings: UserVocabularySettings, failedToday: Boolean): UserProgress {
        val elapsed = old.last?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.parse(day)).toInt() } ?: 0
        val forgottenAfterDelay = rating == Rating.UNKNOWN && elapsed >= 1 && old.recognition > 0
        val recognition = when (rating) {
            Rating.KNOWN -> (old.recognition + 1).coerceAtMost(3)
            Rating.UNKNOWN -> (old.recognition - 1).coerceAtLeast(0)
            Rating.FUZZY -> old.recognition
        }
        val lapses = old.delayedLapses + if (forgottenAfterDelay) 1 else 0
        val recovery = when {
            rating != Rating.KNOWN -> 0
            elapsed >= 1 -> old.recoveryStreak + 1
            else -> old.recoveryStreak
        }
        val difficult = when {
            forgottenAfterDelay && lapses >= 2 -> true
            old.difficult && recovery >= 3 -> false
            else -> old.difficult
        }
        val difficulty = (old.difficulty + when (rating) {
            Rating.UNKNOWN -> if (forgottenAfterDelay) 0.8 else 0.3
            Rating.FUZZY -> 0.2
            Rating.KNOWN -> if (elapsed >= 1) -0.2 else 0.0
        }).coerceIn(1.0, 5.0)
        val base = when {
            rating != Rating.KNOWN || failedToday -> settings.shortIntervals.first()
            recognition >= 3 -> settings.longIntervalDays
            else -> settings.shortIntervals[(recognition - 1).coerceIn(0, 1)]
        }
        val factor = if (settings.adaptiveReview) 1.0 + (difficulty - 2.5).coerceAtLeast(0.0) * 0.35 +
            (if (difficult) 0.5 else 0.0) else 1.0
        var interval = (base / factor).roundToInt().coerceAtLeast(1)
        if (recognition >= 3 && rating == Rating.KNOWN && !failedToday) interval = interval.coerceAtLeast(30)
        if (forgottenAfterDelay && settings.adaptiveReview) interval = minOf(interval, (elapsed / 2).coerceAtLeast(1))
        return old.copy(
            recognition = recognition, difficulty = difficulty, delayedLapses = lapses,
            successes = old.successes + if (rating == Rating.KNOWN) 1 else 0,
            failures = old.failures + if (rating == Rating.UNKNOWN) 1 else 0,
            intervalDays = interval, difficult = difficult, recoveryStreak = recovery,
            first = old.first ?: day, last = day, due = addDays(day, interval.toLong()),
            stage = when { rating == Rating.UNKNOWN -> -1; rating == Rating.KNOWN && !failedToday -> (old.stage + 1).coerceAtMost(5); else -> old.stage },
            state = if (rating == Rating.KNOWN && !failedToday) ProgressState.REVIEW else ProgressState.LEARNING
        )
    }
}

package io.github.cuimiles.studydesk.core.vocabulary

import kotlinx.serialization.Serializable

@Serializable
enum class Rating {
    UNKNOWN,
    FUZZY,
    KNOWN
}

@Serializable
enum class AttemptMode {
    NEW,
    REVIEW,
    RETRY
}

@Serializable
enum class ProgressState {
    LEARNING,
    REVIEW,
    FAMILIAR
}

@Serializable
data class UserProgress(
    val state: ProgressState = ProgressState.LEARNING,
    val stage: Int = -1,
    val due: String? = null,
    val first: String? = null,
    val last: String? = null
)

@Serializable
data class Attempt(
    val id: String,
    val entryId: String,
    val mode: AttemptMode
)

@Serializable
data class LearningSession(
    val day: String,
    val queue: List<Attempt> = emptyList(),
    val retries: Map<String, Int> = emptyMap(),
    val failed: List<String> = emptyList()
)

@Serializable
data class DailyStats(
    val newIds: List<String> = emptyList(),
    val reviewIds: List<String> = emptyList(),
    val attempts: Int = 0,
    val spellingAttempts: Int = 0,
    val spellingCorrect: Int = 0,
    val spellingIds: List<String> = emptyList()
)

@Serializable
data class ReviewEvent(
    val id: String,
    val entryId: String,
    val day: String,
    val rating: Rating,
    val mode: AttemptMode
)

@Serializable
data class UserVocabularySettings(
    val dailyNewLimit: Int = 20,
    val familiarHintSeen: Boolean = false
)

@Serializable
data class VocabularyStore(
    val schemaVersion: Int = 1,
    val revision: Long = 0,
    val settings: UserVocabularySettings = UserVocabularySettings(),
    val progress: Map<String, UserProgress> = emptyMap(),
    val favorites: List<String> = emptyList(),
    val daily: Map<String, DailyStats> = emptyMap(),
    val events: List<ReviewEvent> = emptyList(),
    val session: LearningSession? = null
)

// Read-only SQLite Dictionary Models
data class Book(
    val id: String,
    val title: String,
    val sourceSha256: String,
    val entryCount: Int
)

data class BookEntry(
    val bookId: String,
    val position: Int,
    val wordId: String,
    val chapter: Int,
    val sourceLine: Int,
    val original: String,
    val glossZh: String,
    val sourcePronunciation: String
)

data class Word(
    val id: String,
    val headword: String,
    val lookup: String,
    val status: String,
    val pronunciation: String
)

data class Sense(
    val wordId: String,
    val id: String,
    val pos: String,
    val definitionEn: String,
    val examples: List<String> = emptyList(),
    val synonyms: List<String> = emptyList()
)

data class Generation(
    val wordId: String,
    val senseId: String,
    val promptSha256: String,
    val inputSha256: String,
    val model: String,
    val generatedAt: String,
    val status: String,
    val payloadJson: String
)

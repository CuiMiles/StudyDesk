package io.github.cuimiles.studydesk.core.qa

import kotlinx.serialization.Serializable

@Serializable
data class QuestionContext(
    val word: String,
    val senseId: String,
    val definition: String
)

@Serializable
data class QuestionRequest(
    val version: Int = 1,
    val requestId: String,
    val question: String,
    val context: QuestionContext,
    val language: String = "en"
)

@Serializable
data class QuestionSource(
    val title: String,
    val url: String
)

@Serializable
data class QuestionAnswer(
    val answer: String,
    val chinese: String? = null,
    val sources: List<QuestionSource> = emptyList()
)

interface QuestionProvider {
    suspend fun ask(request: QuestionRequest): Result<QuestionAnswer>
}

package io.github.cuimiles.studydesk.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.cuimiles.studydesk.core.timetable.*
import io.github.cuimiles.studydesk.core.vocabulary.*
import io.github.cuimiles.studydesk.core.calendar.validDate

@Serializable
data class WorkspaceBackup(
    val workspaceVersion: Int = 1,
    val timetable: TimetableStore,
    val vocabulary: VocabularyStore
) {
    companion object {
        fun parse(text: String): WorkspaceBackup {
            require(text.length <= 20_000_000) { "备份过大" }
            val data = Json.decodeFromString<WorkspaceBackup>(text)
            require(data.workspaceVersion == 1 && data.vocabulary.schemaVersion == 1) { "不支持的备份版本" }
            TimetableBackup.parse(Json { encodeDefaults = true }.encodeToString(data.timetable), true)
            val v = data.vocabulary
            require(v.settings.dailyNewLimit in 0..100 && v.revision >= 0)
            require(v.progress.size <= 100000 && v.events.size <= 1000)
            v.progress.forEach { (id, p) ->
                require(id.isNotBlank() && p.stage in -1..5)
                listOfNotNull(p.due, p.first, p.last).forEach { require(validDate(it)) }
            }
            v.daily.forEach { (day, s) ->
                require(validDate(day) && s.attempts >= 0 && s.spellingAttempts >= 0 && s.spellingCorrect in 0..s.spellingAttempts)
            }
            v.session?.let { session ->
                require(validDate(session.day))
                require(session.queue.map { it.id }.distinct().size == session.queue.size)
                require(session.retries.values.all { it in 0..2 })
            }
            return data
        }
    }
}

package io.github.cuimiles.studydesk.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.cuimiles.studydesk.core.vocabulary.Book
import io.github.cuimiles.studydesk.core.vocabulary.BookEntry
import io.github.cuimiles.studydesk.core.vocabulary.Generation
import io.github.cuimiles.studydesk.core.vocabulary.Sense
import io.github.cuimiles.studydesk.core.vocabulary.Word
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream

class ContentDatabaseHelper(private val context: Context) {

    private val dbName = "content.db"
    private var database: SQLiteDatabase? = null

    private fun ensureDatabase(): SQLiteDatabase {
        if (database != null && database!!.isOpen) return database!!

        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(dbName).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        database = db
        return db
    }

    fun getBook(): Book? {
        val db = ensureDatabase()
        val cursor = db.rawQuery("SELECT id, title, source_sha256, entry_count FROM book LIMIT 1", null)
        return cursor.use {
            if (it.moveToFirst()) {
                Book(it.getString(0), it.getString(1), it.getString(2), it.getInt(3))
            } else null
        }
    }

    fun getChapters(): List<Int> {
        val db = ensureDatabase()
        val cursor = db.rawQuery("SELECT DISTINCT chapter FROM book_entry ORDER BY chapter ASC", null)
        val list = mutableListOf<Int>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getInt(0))
            }
        }
        return list
    }

    fun getEntriesByChapter(chapter: Int): List<BookEntry> {
        val db = ensureDatabase()
        val cursor = db.rawQuery(
            "SELECT book_id, position, word_id, chapter, source_line, original, gloss_zh, source_pronunciation " +
                    "FROM book_entry WHERE chapter = ? ORDER BY position ASC",
            arrayOf(chapter.toString())
        )
        val list = mutableListOf<BookEntry>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BookEntry(
                        bookId = it.getString(0),
                        position = it.getInt(1),
                        wordId = it.getString(2),
                        chapter = it.getInt(3),
                        sourceLine = it.getInt(4),
                        original = it.getString(5),
                        glossZh = it.getString(6),
                        sourcePronunciation = it.getString(7)
                    )
                )
            }
        }
        return list
    }

    fun getAllWordIds(): List<String> {
        val db = ensureDatabase()
        val cursor = db.rawQuery("SELECT DISTINCT word_id FROM book_entry ORDER BY position ASC", null)
        val list = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    fun getWord(wordId: String): Word? {
        val db = ensureDatabase()
        val cursor = db.rawQuery(
            "SELECT id, headword, lookup, status, pronunciation FROM word WHERE id = ? LIMIT 1",
            arrayOf(wordId)
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Word(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4))
            } else null
        }
    }

    fun getSenses(wordId: String): List<Sense> {
        val db = ensureDatabase()
        val cursor = db.rawQuery(
            "SELECT word_id, id, pos, definition_en, examples_json, synonyms_json FROM sense WHERE word_id = ?",
            arrayOf(wordId)
        )
        val list = mutableListOf<Sense>()
        cursor.use {
            while (it.moveToNext()) {
                val examplesJson = it.getString(4)
                val synonymsJson = it.getString(5)
                val examples = try {
                    Json.parseToJsonElement(examplesJson).jsonArray.map { e -> e.jsonPrimitive.content }
                } catch (e: Exception) {
                    emptyList()
                }
                val synonyms = try {
                    Json.parseToJsonElement(synonymsJson).jsonArray.map { s -> s.jsonPrimitive.content }
                } catch (e: Exception) {
                    emptyList()
                }
                list.add(
                    Sense(
                        wordId = it.getString(0),
                        id = it.getString(1),
                        pos = it.getString(2),
                        definitionEn = it.getString(3),
                        examples = examples,
                        synonyms = synonyms
                    )
                )
            }
        }
        return list
    }

    fun getGeneration(wordId: String): Generation? {
        val db = ensureDatabase()
        val cursor = db.rawQuery(
            "SELECT word_id, sense_id, prompt_sha256, input_sha256, model, generated_at, status, payload_json " +
                    "FROM generation WHERE word_id = ? LIMIT 1",
            arrayOf(wordId)
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Generation(
                    wordId = it.getString(0),
                    senseId = it.getString(1),
                    promptSha256 = it.getString(2),
                    inputSha256 = it.getString(3),
                    model = it.getString(4),
                    generatedAt = it.getString(5),
                    status = it.getString(6),
                    payloadJson = it.getString(7)
                )
            } else null
        }
    }

    fun searchWords(query: String, limit: Int = 30): List<Word> {
        if (query.isBlank()) return emptyList()
        val db = ensureDatabase()
        val normalized = query.trim().lowercase()
        val cursor = db.rawQuery(
            "SELECT id, headword, lookup, status, pronunciation FROM word " +
                    "WHERE lookup LIKE ? OR headword LIKE ? LIMIT ?",
            arrayOf("$normalized%", "$normalized%", limit.toString())
        )
        val list = mutableListOf<Word>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(Word(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4)))
            }
        }
        return list
    }

    fun close() {
        database?.close()
        database = null
    }
}

package io.github.cuimiles.studydesk.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.cuimiles.studydesk.core.timetable.Course
import io.github.cuimiles.studydesk.core.timetable.CourseAdjustment
import io.github.cuimiles.studydesk.core.vocabulary.ProgressState
import io.github.cuimiles.studydesk.core.vocabulary.UserProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class UserDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "userdata.db", null, 1) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE course (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                class_name TEXT NOT NULL,
                teachers_json TEXT NOT NULL,
                room TEXT NOT NULL,
                weekday INTEGER NOT NULL,
                sections_json TEXT NOT NULL,
                weeks_json TEXT NOT NULL,
                note TEXT NOT NULL
            );
            """
        )
        db.execSQL(
            """
            CREATE TABLE course_adjustment (
                course_id TEXT NOT NULL,
                original_date TEXT NOT NULL,
                cancelled INTEGER NOT NULL,
                date TEXT NOT NULL,
                sections_json TEXT NOT NULL,
                room TEXT NOT NULL,
                PRIMARY KEY (course_id, original_date)
            );
            """
        )
        db.execSQL(
            """
            CREATE TABLE vocabulary_progress (
                word_id TEXT PRIMARY KEY,
                state TEXT NOT NULL,
                stage INTEGER NOT NULL,
                due TEXT,
                first_date TEXT,
                last_date TEXT
            );
            """
        )
        db.execSQL("CREATE TABLE vocabulary_favorite (word_id TEXT PRIMARY KEY);")
        db.execSQL("CREATE TABLE app_setting (key TEXT PRIMARY KEY, value TEXT NOT NULL);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun transaction(block: () -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { block(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    // Course operations
    fun getAllCourses(): List<Course> {
        val list = mutableListOf<Course>()
        val cursor = readableDatabase.rawQuery("SELECT id, name, class_name, teachers_json, room, weekday, sections_json, weeks_json, note FROM course", null)
        cursor.use {
            while (it.moveToNext()) {
                val teachers = try {
                    json.parseToJsonElement(it.getString(3)).jsonArray.map { t -> t.jsonPrimitive.content }
                } catch (e: Exception) { emptyList() }
                val sections = try {
                    json.parseToJsonElement(it.getString(6)).jsonArray.map { s -> s.jsonPrimitive.content.toInt() }
                } catch (e: Exception) { emptyList() }
                val weeks = try {
                    json.parseToJsonElement(it.getString(7)).jsonArray.map { w -> w.jsonPrimitive.content.toInt() }
                } catch (e: Exception) { emptyList() }

                list.add(
                    Course(
                        id = it.getString(0),
                        name = it.getString(1),
                        className = it.getString(2),
                        teachers = teachers,
                        room = it.getString(4),
                        weekday = it.getInt(5),
                        sections = sections,
                        weeks = weeks,
                        note = it.getString(8)
                    )
                )
            }
        }
        return list
    }

    fun saveCourse(course: Course) {
        val cv = ContentValues().apply {
            put("id", course.id)
            put("name", course.name)
            put("class_name", course.className)
            put("teachers_json", json.encodeToString(course.teachers))
            put("room", course.room)
            put("weekday", course.weekday)
            put("sections_json", json.encodeToString(course.sections))
            put("weeks_json", json.encodeToString(course.weeks))
            put("note", course.note)
        }
        check(writableDatabase.insertWithOnConflict("course", null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "保存失败" }
    }

    fun deleteCourse(courseId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("course", "id = ?", arrayOf(courseId))
            db.delete("course_adjustment", "course_id = ?", arrayOf(courseId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAndSetCourses(courses: List<Course>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("course", null, null)
            for (c in courses) {
                saveCourse(c)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Adjustment operations
    fun getAllAdjustments(): List<CourseAdjustment> {
        val list = mutableListOf<CourseAdjustment>()
        val cursor = readableDatabase.rawQuery("SELECT course_id, original_date, cancelled, date, sections_json, room FROM course_adjustment", null)
        cursor.use {
            while (it.moveToNext()) {
                val sections = try {
                    json.parseToJsonElement(it.getString(4)).jsonArray.map { s -> s.jsonPrimitive.content.toInt() }
                } catch (e: Exception) { emptyList() }
                list.add(
                    CourseAdjustment(
                        courseId = it.getString(0),
                        originalDate = it.getString(1),
                        cancelled = it.getInt(2) != 0,
                        date = it.getString(3),
                        sections = sections,
                        room = it.getString(5)
                    )
                )
            }
        }
        return list
    }

    fun saveAdjustment(adj: CourseAdjustment) {
        val cv = ContentValues().apply {
            put("course_id", adj.courseId)
            put("original_date", adj.originalDate)
            put("cancelled", if (adj.cancelled) 1 else 0)
            put("date", adj.date)
            put("sections_json", json.encodeToString(adj.sections))
            put("room", adj.room)
        }
        check(writableDatabase.insertWithOnConflict("course_adjustment", null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "保存失败" }
    }

    fun deleteAdjustment(courseId: String, originalDate: String) {
        writableDatabase.delete(
            "course_adjustment",
            "course_id = ? AND original_date = ?",
            arrayOf(courseId, originalDate)
        )
    }

    fun clearAndSetAdjustments(adjustments: List<CourseAdjustment>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("course_adjustment", null, null)
            for (a in adjustments) {
                saveAdjustment(a)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Vocabulary progress
    fun getProgressMap(): Map<String, UserProgress> {
        val map = mutableMapOf<String, UserProgress>()
        val cursor = readableDatabase.rawQuery("SELECT word_id, state, stage, due, first_date, last_date FROM vocabulary_progress", null)
        cursor.use {
            while (it.moveToNext()) {
                val state = try {
                    ProgressState.valueOf(it.getString(1))
                } catch (e: Exception) {
                    ProgressState.LEARNING
                }
                map[it.getString(0)] = UserProgress(
                    state = state,
                    stage = it.getInt(2),
                    due = if (it.isNull(3)) null else it.getString(3),
                    first = if (it.isNull(4)) null else it.getString(4),
                    last = if (it.isNull(5)) null else it.getString(5)
                )
            }
        }
        return map
    }

    fun saveProgress(wordId: String, progress: UserProgress) {
        val cv = ContentValues().apply {
            put("word_id", wordId)
            put("state", progress.state.name)
            put("stage", progress.stage)
            put("due", progress.due)
            put("first_date", progress.first)
            put("last_date", progress.last)
        }
        writableDatabase.insertWithOnConflict("vocabulary_progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveProgressMap(map: Map<String, UserProgress>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((wid, p) in map) {
                saveProgress(wid, p)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Favorites
    fun getFavorites(): Set<String> {
        val set = mutableSetOf<String>()
        val cursor = readableDatabase.rawQuery("SELECT word_id FROM vocabulary_favorite", null)
        cursor.use {
            while (it.moveToNext()) {
                set.add(it.getString(0))
            }
        }
        return set
    }

    fun setFavorite(wordId: String, isFav: Boolean) {
        if (isFav) {
            val cv = ContentValues().apply { put("word_id", wordId) }
            writableDatabase.insertWithOnConflict("vocabulary_favorite", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            writableDatabase.delete("vocabulary_favorite", "word_id = ?", arrayOf(wordId))
        }
    }

    // Settings
    fun getSetting(key: String, defaultVal: String = ""): String {
        val cursor = readableDatabase.rawQuery("SELECT value FROM app_setting WHERE key = ?", arrayOf(key))
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else defaultVal
        }
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        check(writableDatabase.insertWithOnConflict("app_setting", null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "保存失败" }
    }

    fun clearAllUserData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("course", null, null)
            db.delete("course_adjustment", null, null)
            db.delete("vocabulary_progress", null, null)
            db.delete("vocabulary_favorite", null, null)
            db.delete("app_setting", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

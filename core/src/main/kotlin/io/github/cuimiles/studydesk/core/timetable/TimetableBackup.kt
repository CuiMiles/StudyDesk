package io.github.cuimiles.studydesk.core.timetable

import io.github.cuimiles.studydesk.core.calendar.validDate
import io.github.cuimiles.studydesk.core.calendar.weekOf
import io.github.cuimiles.studydesk.core.calendar.weekdayOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

object TimetableBackup {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun parse(text: String, isBackup: Boolean = false): TimetableStore {
        if (text.length > 2_000_000) {
            throw IllegalArgumentException("JSON过大，请控制在2MB以内")
        }
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON格式错误：请检查引号、逗号，勿包含 Markdown 代码围栏: ${e.message}")
        }

        if (element !is JsonObject) {
            throw IllegalArgumentException("数据必须是对象")
        }

        val schemaVersion = element["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("缺少 schemaVersion")
        val semesterId = element["semesterId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("缺少 semesterId")

        if (schemaVersion != 1 || semesterId != "2026-fall") {
            throw IllegalArgumentException("仅支持 schemaVersion: 1 和 semesterId: \"2026-fall\"")
        }

        val coursesArray = element["courses"]?.jsonArray
            ?: throw IllegalArgumentException("courses须为数组")
        if (coursesArray.size > 500) {
            throw IllegalArgumentException("courses最多500项")
        }

        val parsedCourses = coursesArray.mapIndexed { idx, item ->
            parseCourse(item, "第${idx + 1}项课程：")
        }

        if (isBackup) {
            val backupVersion = element["backupVersion"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("不是完整备份：缺少 backupVersion: 1")
            if (backupVersion != 1) {
                throw IllegalArgumentException("仅支持 backupVersion: 1")
            }

            val courseIds = parsedCourses.map { it.id }
            if (courseIds.any { it.isBlank() || it.length > 100 }) {
                throw IllegalArgumentException("备份课程ID缺失或过长")
            }
            if (courseIds.toSet().size != courseIds.size) {
                throw IllegalArgumentException("备份课程ID重复")
            }

            val adjustmentsArray = element["adjustments"]?.jsonArray ?: JsonArray(emptyList())
            if (adjustmentsArray.size > 10000) {
                throw IllegalArgumentException("备份调课数据无效（最多10000条）")
            }

            val keys = mutableSetOf<String>()
            val parsedAdjustments = adjustmentsArray.map { adjElem ->
                parseAdjustment(adjElem, parsedCourses, keys)
            }

            return TimetableStore(
                schemaVersion = 1,
                semesterId = "2026-fall",
                courses = parsedCourses,
                adjustments = parsedAdjustments,
                backupVersion = 1
            )
        } else {
            if (element.containsKey("adjustments") || element.containsKey("backupVersion")) {
                throw IllegalArgumentException("这是备份数据，请切换到“恢复备份”以保留调课")
            }
            // Auto generate IDs for imported courses if missing
            val coursesWithIds = parsedCourses.map {
                if (it.id.isBlank()) it.copy(id = "course-" + UUID.randomUUID().toString().substring(0, 8))
                else it
            }
            return TimetableStore(
                schemaVersion = 1,
                semesterId = "2026-fall",
                courses = coursesWithIds,
                adjustments = emptyList(),
                backupVersion = null
            )
        }
    }

    private fun parseCourse(elem: JsonElement, prefix: String): Course {
        if (elem !is JsonObject) throw IllegalArgumentException("$prefix 必须是对象")

        val name = checkStr(elem["name"]?.jsonPrimitive?.contentOrNull, "$prefix 名称", required = true)
        val className = checkStr(elem["className"]?.jsonPrimitive?.contentOrNull ?: "", "$prefix 班级")
        val room = checkStr(elem["room"]?.jsonPrimitive?.contentOrNull ?: "", "$prefix 教室")
        val note = checkStr(elem["note"]?.jsonPrimitive?.contentOrNull ?: "", "$prefix 备注")

        val weekday = elem["weekday"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("$prefix 星期须为整数")
        if (weekday !in 1..7) throw IllegalArgumentException("$prefix 星期须为1～7")

        val teachersArray = elem["teachers"]?.jsonArray ?: JsonArray(emptyList())
        if (teachersArray.size > 20) throw IllegalArgumentException("$prefix 教师最多20项")
        val teachers = teachersArray.map {
            checkStr(it.jsonPrimitive.contentOrNull, "$prefix 教师", required = true)
        }

        val sections = checkNumbers(elem["sections"], 11, "$prefix 节次")
        val weeks = checkNumbers(elem["weeks"], 18, "$prefix 周次")
        val id = elem["id"]?.jsonPrimitive?.contentOrNull ?: ""

        return Course(
            id = id,
            name = name,
            className = className,
            teachers = teachers,
            room = room,
            weekday = weekday,
            sections = sections,
            weeks = weeks,
            note = note
        )
    }

    private fun parseAdjustment(
        elem: JsonElement,
        courses: List<Course>,
        seenKeys: MutableSet<String>
    ): CourseAdjustment {
        if (elem !is JsonObject) throw IllegalArgumentException("调课项必须是对象")

        val courseId = elem["courseId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("调课缺少 courseId")
        val originalDate = elem["originalDate"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("调课缺少 originalDate")

        val course = courses.find { it.id == courseId }
            ?: throw IllegalArgumentException("调课对应课程未找到: $courseId")

        if (!validDate(originalDate) ||
            weekdayOf(originalDate) != course.weekday ||
            !course.weeks.contains(weekOf(originalDate))
        ) {
            throw IllegalArgumentException("调课原日期不属于基础课程: $originalDate")
        }

        val key = "$courseId@$originalDate"
        if (seenKeys.contains(key)) {
            throw IllegalArgumentException("同一次课程有重复调课: $key")
        }
        seenKeys.add(key)

        val cancelled = elem["cancelled"]?.jsonPrimitive?.booleanOrNull ?: false
        val targetDate = elem["date"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("调课缺少目标 date")
        if (!validDate(targetDate) || weekOf(targetDate) !in 1..18) {
            throw IllegalArgumentException("调课目标须在本学期1～18周内: $targetDate")
        }

        val sections = checkNumbers(elem["sections"], 11, "调课节次")
        val room = checkStr(elem["room"]?.jsonPrimitive?.contentOrNull ?: "", "调课教室")

        return CourseAdjustment(
            courseId = courseId,
            originalDate = originalDate,
            cancelled = cancelled,
            date = targetDate,
            sections = sections,
            room = room
        )
    }

    private fun checkStr(v: String?, p: String, required: Boolean = false): String {
        if (v == null || v.length > 500 || (required && v.trim().isEmpty())) {
            throw IllegalArgumentException("$p 必须是${if (required) "非空" else ""}文本（最多500字）")
        }
        return v.trim()
    }

    private fun checkNumbers(elem: JsonElement?, max: Int, p: String): List<Int> {
        val arr = elem?.jsonArray ?: throw IllegalArgumentException("$p 须为数组")
        if (arr.isEmpty() || arr.size > max) {
            throw IllegalArgumentException("$p 须为1～$max 的不重复整数数组，且不能为空")
        }
        val nums = arr.map {
            it.jsonPrimitive.intOrNull ?: throw IllegalArgumentException("$p 项须为整数")
        }
        if (nums.any { it < 1 || it > max } || nums.toSet().size != nums.size) {
            throw IllegalArgumentException("$p 须为1～$max 的不重复整数数组")
        }
        return nums.sorted()
    }

    fun exportBackup(store: TimetableStore): String {
        return json.encodeToString(store.copy(backupVersion = 1))
    }

    fun exportCoursesOnly(courses: List<Course>): String {
        val minimalStore = TimetableStore(
            schemaVersion = 1,
            semesterId = "2026-fall",
            courses = courses,
            adjustments = emptyList(),
            backupVersion = null
        )
        return json.encodeToString(minimalStore)
    }
}

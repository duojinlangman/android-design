package com.example.dancijiyi.data

import java.util.concurrent.TimeUnit

/** 一个单词 */
data class Word(
    val id: Long = 0,
    val bookId: Long = 0,
    val word: String = "",
    /** 美式音标，例如 /əˈbændən/ */
    val phonetic: String = "",
    val meaning: String = "",
    val example: String = "",
    /** 已成功记忆的次数，对应艾宾浩斯阶段 */
    val stage: Int = 0,
    val nextReviewAt: Long = 0,
    val createdAt: Long = 0,
    val lastReviewAt: Long = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val mastered: Boolean = false,
    val starred: Boolean = false
) {
    val totalCount: Int get() = correctCount + wrongCount
    val accuracy: Int
        get() = if (totalCount == 0) 0 else (correctCount * 100 / totalCount)

    /** 下一次复习的说明文案 */
    val nextReviewText: String
        get() = when {
            mastered -> "已掌握"
            nextReviewAt <= System.currentTimeMillis() -> "现在该复习"
            else -> ReviewPlan.humanize(nextReviewAt - System.currentTimeMillis()) + "后复习"
        }

    val stageText: String get() = ReviewPlan.stageLabel(stage)
}

/** 词库 */
data class WordBook(
    val id: Long,
    val name: String,
    val isSystem: Boolean,
    val createdAt: Long,
    val wordCount: Int,
    val dueCount: Int,
    val masteredCount: Int,
    val coverColor: Int = 0
) {
    val progress: Int get() = if (wordCount == 0) 0 else masteredCount * 100 / wordCount
}

/** 看板汇总数据 */
data class DashboardStats(
    val totalWords: Int = 0,
    val masteredWords: Int = 0,
    val learningWords: Int = 0,
    val newWords: Int = 0,
    val dueNow: Int = 0,
    val todayLearned: Int = 0,
    val todayReviewed: Int = 0,
    val todayCorrect: Int = 0,
    val totalReviews: Int = 0,
    val totalCorrect: Int = 0,
    val streakDays: Int = 0,
    val last7: List<DayPoint> = emptyList()
) {
    val todayAccuracy: Int
        get() = if (todayReviewed == 0) 0 else todayCorrect * 100 / todayReviewed
    val masteredPercent: Int
        get() = if (totalWords == 0) 0 else masteredWords * 100 / totalWords
    val overallAccuracy: Int
        get() = if (totalReviews == 0) 0 else totalCorrect * 100 / totalReviews
}

/** 某一天的学习数据（看板图表用） */
data class DayPoint(
    val dayStart: Long,
    val reviewed: Int,
    val learned: Int,
    val correct: Int,
    val label: String
)

/** 测试题 */
data class QuizQuestion(
    val word: Word,
    val options: List<String>,
    val correctIndex: Int
)

/** 复习评分结果 */
enum class ReviewGrade { FORGOT, VAGUE, KNOWN }

/**
 * 艾宾浩斯复习计划：
 * 新词 → 5 分钟 → 30 分钟 → 12 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天 → 掌握
 */
object ReviewPlan {
    private const val MINUTE = 60L * 1000L
    private const val HOUR = 60L * MINUTE
    private const val DAY = 24L * HOUR

    /** 各阶段"间隔"，单位毫秒 */
    val intervals: LongArray = longArrayOf(
        5 * MINUTE,
        30 * MINUTE,
        12 * HOUR,
        DAY,
        2 * DAY,
        4 * DAY,
        7 * DAY,
        15 * DAY,
        30 * DAY
    )

    /** 掌握之后再巩固一次的间隔 */
    const val MASTER_INTERVAL = 90L * DAY

    fun stageLabel(stage: Int): String = when {
        stage <= 0 -> "新词"
        stage >= intervals.size -> "已掌握"
        else -> "第 $stage 阶段 · ${humanize(intervals[stage])}后复习"
    }

    /** 下一次复习时间 */
    fun nextTimeFor(stage: Int, grade: ReviewGrade, now: Long = System.currentTimeMillis()): Pair<Int, Long> {
        return when (grade) {
            ReviewGrade.FORGOT -> 0 to now + intervals[0]
            ReviewGrade.VAGUE -> {
                val s = stage.coerceIn(0, intervals.size - 1)
                s to now + (intervals[s] / 2).coerceAtLeast(intervals[0])
            }
            ReviewGrade.KNOWN -> {
                val next = stage + 1
                if (next >= intervals.size) {
                    Int.MAX_VALUE to now + MASTER_INTERVAL
                } else {
                    next to now + intervals[next]
                }
            }
        }
    }

    fun humanize(durationMs: Long): String {
        if (durationMs <= 0) return "马上"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        return when {
            minutes < 1 -> "不到 1 分钟"
            minutes < 60 -> "$minutes 分钟"
            minutes < 60 * 24 -> "${minutes / 60} 小时"
            else -> "${minutes / (60 * 24)} 天"
        }
    }
}

package com.example.mycalories.domain

import com.example.mycalories.domain.model.MealType
import java.time.LocalDateTime
import java.time.LocalTime

/** Learns when the user usually eats, and decides when a "not logged yet" reminder is due. */
object MealTimes {
    val DEFAULTS: Map<MealType, LocalTime> = mapOf(
        MealType.BREAKFAST to LocalTime.of(8, 30),
        MealType.LUNCH to LocalTime.of(13, 30),
        MealType.SNACK to LocalTime.of(17, 0),
        MealType.DINNER to LocalTime.of(20, 30),
    )
    private const val MIN_SAMPLES = 3
    private const val GRACE_MINUTES = 45L
    private const val GIVE_UP_HOURS = 3L

    /** Median first-log time per meal; defaults until a meal has 3 days of history. */
    fun usual(firstLogTimes: Map<MealType, List<LocalTime>>): Map<MealType, LocalTime> =
        DEFAULTS.mapValues { (meal, default) ->
            val times = firstLogTimes[meal].orEmpty().sorted()
            if (times.size < MIN_SAMPLES) default else times[times.size / 2]
        }

    fun dueReminders(
        now: LocalDateTime,
        usual: Map<MealType, LocalTime>,
        loggedToday: Set<MealType>,
        remindedToday: Set<MealType>,
    ): List<MealType> = usual.filter { (meal, time) ->
        val start = now.toLocalDate().atTime(time).plusMinutes(GRACE_MINUTES)
        val end = now.toLocalDate().atTime(time).plusHours(GIVE_UP_HOURS)
        meal !in loggedToday && meal !in remindedToday && !now.isBefore(start) && now.isBefore(end)
    }.keys.toList()
}

object WaterPolicy {
    const val GOAL = 8
    private val START = LocalTime.of(9, 0)
    private val END = LocalTime.of(21, 0)

    fun shouldRemind(now: LocalTime, glasses: Int, lastReminder: LocalTime?): Boolean =
        glasses < GOAL && !now.isBefore(START) && now.isBefore(END) &&
            (lastReminder == null || !now.isBefore(lastReminder.plusHours(2)))
}

package com.example.mycalories.domain

import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

/** Shows notifications. Android's NotificationManager in the app; a fake in tests. */
interface Notifier {
    fun mealReminder(meal: MealType)
    fun waterReminder(glasses: Int, goal: Int)
}

/** Runs every ~30 minutes in the background and decides which reminders to show. */
class ReminderChecker(
    private val log: LogRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    suspend fun run() {
        val s = settings.settings.first()
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()

        if (s.mealReminders) {
            val history = log.observeRange(today.minusDays(14), today.minusDays(1)).first()
            val firstTimes: Map<MealType, List<LocalTime>> = history
                .groupBy { it.meal }
                .mapValues { (_, items) ->
                    items.groupBy { it.date }.values.map { day -> timeOf(day.minOf { it.loggedAtMillis }) }
                }
            val loggedToday = log.observeDay(today).first().map { it.meal }.toSet()
            MealTimes.dueReminders(now, MealTimes.usual(firstTimes), loggedToday, settings.remindedMeals(today)).forEach { meal ->
                notifier.mealReminder(meal)
                settings.markReminded(today, meal)
            }
        }

        if (s.waterReminders) {
            val glasses = log.observeWater(today).first()
            if (WaterPolicy.shouldRemind(now.toLocalTime(), glasses, settings.lastWaterReminder(today))) {
                notifier.waterReminder(glasses, WaterPolicy.GOAL)
                settings.setLastWaterReminder(today, now.toLocalTime())
            }
        }
    }

    private fun timeOf(millis: Long): LocalTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), clock.zone).toLocalTime()
}

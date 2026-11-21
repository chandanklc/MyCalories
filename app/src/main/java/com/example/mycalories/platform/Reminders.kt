package com.example.mycalories.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mycalories.MainActivity
import com.example.mycalories.MyCaloriesApp
import com.example.mycalories.R
import com.example.mycalories.domain.Notifier
import com.example.mycalories.domain.ReminderChecker
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.ReminderScheduler
import java.util.concurrent.TimeUnit

/** Turns the background check on while any reminder is switched on. */
class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun apply(settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (settings.mealReminders || settings.waterReminders) {
            wm.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    companion object { const val WORK_NAME = "reminders" }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MyCaloriesApp).container
        ReminderChecker(c.log, c.settings, AndroidNotifier(applicationContext), c.clock).run()
        return Result.success()
    }
}

class AndroidNotifier(private val context: Context) : Notifier {

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun allowed() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(action: String?, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action?.let { putExtra(MainActivity.EXTRA_OPEN, it) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun mealReminder(meal: MealType) {
        if (!allowed()) return
        ensureChannel()
        val mealName = context.getString(mealLabelRes(meal))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_food)
            .setContentTitle(context.getString(R.string.notif_meal_title, mealName))
            .setContentText(context.getString(R.string.notif_meal_text))
            .setContentIntent(open(MainActivity.OPEN_LOG, 10 + meal.ordinal))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(100 + meal.ordinal, n)
    }

    override fun waterReminder(glasses: Int, goal: Int) {
        if (!allowed()) return
        ensureChannel()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_food)
            .setContentTitle(context.getString(R.string.notif_water_title))
            .setContentText(context.getString(R.string.notif_water_text, glasses, goal))
            .setContentIntent(open(MainActivity.OPEN_ADD_WATER, 20))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(200, n)
    }

    companion object {
        const val CHANNEL = "reminders"

        fun mealLabelRes(meal: MealType): Int = when (meal) {
            MealType.BREAKFAST -> R.string.meal_breakfast
            MealType.LUNCH -> R.string.meal_lunch
            MealType.SNACK -> R.string.meal_snack
            MealType.DINNER -> R.string.meal_dinner
        }
    }
}

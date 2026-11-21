package com.example.mycalories.platform

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.mycalories.MainActivity
import com.example.mycalories.MyCaloriesApp
import com.example.mycalories.R
import com.example.mycalories.domain.DaySummary
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Home-screen widget: calories left today and a button that opens straight into logging a meal. */
class CaloriesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as MyCaloriesApp).container
        val today = LocalDate.now(c.clock)
        val goal = c.profiles.profile.first()?.let(GoalCalculator::calculate) ?: TodayViewModel.DEFAULT_GOAL
        val summary = DaySummary.of(today, c.log.observeDay(today).first(), goal)
        val left = summary.remainingCalories
        val e = summary.eaten
        val leftLabel = context.getString(if (left >= 0) R.string.kcal_left else R.string.kcal_over)
        val macros = context.getString(R.string.widget_macros, e.protein.roundToInt(), e.carbs.roundToInt(), e.fat.roundToInt())
        val logLabel = context.getString(R.string.widget_log)

        provideContent { GlanceTheme { Content(abs(left), leftLabel, macros, logLabel) } }
    }

    @Composable
    private fun Content(left: Int, leftLabel: String, macros: String, logLabel: String) {
        val open = actionStartActivity(
            Intent().setClassName("com.example.mycalories", MainActivity::class.java.name)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_LOG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        Row(
            GlanceModifier.fillMaxSize().background(Color(0xFFF6F7F3)).cornerRadius(24.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text("%,d".format(left), style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color(0xFF15201A))))
                Text(leftLabel, style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF5B665F))))
                Text(macros, style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF5B665F))))
            }
            Spacer(GlanceModifier.width(8.dp))
            Box(
                GlanceModifier.size(56.dp).background(Color(0xFF1E7F55)).cornerRadius(18.dp).clickable(open),
                contentAlignment = Alignment.Center,
            ) {
                Text(logLabel, style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }
        }
    }
}

class CaloriesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaloriesWidget()
}

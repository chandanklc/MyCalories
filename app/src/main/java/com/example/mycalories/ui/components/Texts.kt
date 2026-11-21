package com.example.mycalories.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mycalories.R
import com.example.mycalories.data.remote.AppError
import com.example.mycalories.domain.MicroKey
import com.example.mycalories.domain.PortionCalculator
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.MealType

@get:StringRes
val MealType.labelRes: Int
    get() = when (this) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.SNACK -> R.string.meal_snack
        MealType.DINNER -> R.string.meal_dinner
    }

@Composable fun MealType.label(): String = stringResource(labelRes)

@Composable
fun AppError.text(): String = stringResource(
    when (this) {
        AppError.NO_SPEECH -> R.string.err_no_speech
        AppError.NO_FOOD -> R.string.err_no_food
        AppError.NETWORK -> R.string.err_network
        AppError.REFUSED -> R.string.err_refused
        AppError.TOO_LONG -> R.string.err_too_long
        AppError.BAD_REPLY -> R.string.err_bad_reply
        AppError.NOT_CONFIGURED -> R.string.err_not_configured
        AppError.MIC_PERMISSION -> R.string.err_mic_permission
        AppError.SPEECH_UNAVAILABLE -> R.string.err_speech_unavailable
        AppError.BARCODE_NOT_FOUND -> R.string.err_barcode_not_found
        AppError.INVALID_BACKUP -> R.string.err_invalid_backup
        AppError.BAD_KEY -> R.string.err_bad_key
        AppError.NO_CREDIT -> R.string.err_no_credit
        AppError.FREE_LIMIT -> R.string.err_free_limit
        AppError.MODEL_UNAVAILABLE -> R.string.err_model_unavailable
        AppError.BUSY -> R.string.err_busy
        AppError.GENERIC -> R.string.err_generic
    },
)

@Composable
fun FoodSource.label(): String = stringResource(
    when (this) {
        FoodSource.LOCAL -> R.string.source_local
        FoodSource.USER_RECIPE -> R.string.source_recipe
        FoodSource.ONLINE_USDA -> R.string.source_usda
        FoodSource.AI_ESTIMATE -> R.string.source_estimate
        FoodSource.PACKAGED -> R.string.source_packaged
    },
)

@Composable
fun ActivityLevel.label(): String = stringResource(
    when (this) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary
        ActivityLevel.LIGHT -> R.string.activity_light
        ActivityLevel.MODERATE -> R.string.activity_moderate
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very
    },
)

@Composable
fun GoalType.label(): String = stringResource(
    when (this) {
        GoalType.LOSE -> R.string.goal_lose
        GoalType.MAINTAIN -> R.string.goal_maintain
        GoalType.GAIN -> R.string.goal_gain
    },
)

@Composable
fun MicroKey.label(): String = stringResource(
    when (this) {
        MicroKey.IRON -> R.string.iron
        MicroKey.CALCIUM -> R.string.calcium
        MicroKey.B12 -> R.string.vitamin_b12
        MicroKey.VITAMIN_D -> R.string.vitamin_d
        MicroKey.VITAMIN_C -> R.string.vitamin_c
        MicroKey.POTASSIUM -> R.string.potassium
        MicroKey.SODIUM -> R.string.sodium
    },
)

@Composable
fun MicroKey.helpText(): String? = when (this) {
    MicroKey.IRON -> stringResource(R.string.help_iron)
    MicroKey.CALCIUM -> stringResource(R.string.help_calcium)
    MicroKey.B12 -> stringResource(R.string.help_b12)
    MicroKey.VITAMIN_D -> stringResource(R.string.help_vitd)
    MicroKey.VITAMIN_C -> stringResource(R.string.help_vitc)
    MicroKey.POTASSIUM -> stringResource(R.string.help_potassium)
    MicroKey.SODIUM -> null
}

/** Known kitchen units in the user's language; anything else as said. */
@Composable
fun unitLabel(unit: String): String {
    val res = when (PortionCalculator.normalize(unit)) {
        "piece", "pc", "pcs" -> R.string.unit_piece
        "bowl" -> R.string.unit_bowl
        "katori" -> R.string.unit_katori
        "cup" -> R.string.unit_cup
        "tbsp", "tablespoon" -> R.string.unit_tbsp
        "tsp", "teaspoon" -> R.string.unit_tsp
        "g", "gram" -> R.string.unit_g
        "glass" -> R.string.unit_glass
        "plate" -> R.string.unit_plate
        "slice" -> R.string.unit_slice
        "serving" -> R.string.unit_serving
        else -> null
    }
    return res?.let { stringResource(it) } ?: unit
}

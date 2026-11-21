package com.example.mycalories.domain

import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.Sex

enum class MicroKey { IRON, CALCIUM, B12, VITAMIN_D, VITAMIN_C, POTASSIUM, SODIUM }

enum class MicroStatus { LOW, OK, MET, OVER }

data class MicroRow(val key: MicroKey, val value: Double, val target: Double, val unit: String, val status: MicroStatus) {
    /** Bar fill from 0 to 1. */
    val progress: Float get() = if (target <= 0) 0f else (value / target).toFloat().coerceIn(0f, 1f)
}

/** Daily vitamin and mineral needs for adults (ICMR-NIN 2020 values, rounded) and how a day compares. */
data class MicroNeeds(
    val ironMg: Double,
    val calciumMg: Double,
    val vitaminB12Ug: Double,
    val vitaminDUg: Double,
    val vitaminCMg: Double,
    val potassiumMg: Double,
    val sodiumLimitMg: Double,
) {
    companion object {
        fun forSex(sex: Sex?): MicroNeeds = MicroNeeds(
            ironMg = if (sex == Sex.FEMALE) 29.0 else 19.0,
            calciumMg = 1000.0,
            vitaminB12Ug = 2.2,
            vitaminDUg = 15.0,
            vitaminCMg = if (sex == Sex.FEMALE) 65.0 else 80.0,
            potassiumMg = 3500.0,
            sodiumLimitMg = 2000.0,
        )

        fun report(eaten: Nutrients, needs: MicroNeeds): List<MicroRow> {
            fun need(key: MicroKey, value: Double, target: Double, unit: String): MicroRow {
                val ratio = if (target > 0) value / target else 0.0
                val status = when {
                    ratio >= 1.0 -> MicroStatus.MET
                    ratio >= 0.5 -> MicroStatus.OK
                    else -> MicroStatus.LOW
                }
                return MicroRow(key, value, target, unit, status)
            }
            val sodium = MicroRow(
                MicroKey.SODIUM, eaten.sodiumMg, needs.sodiumLimitMg, "mg",
                if (eaten.sodiumMg > needs.sodiumLimitMg) MicroStatus.OVER else MicroStatus.OK,
            )
            return listOf(
                need(MicroKey.IRON, eaten.ironMg, needs.ironMg, "mg"),
                need(MicroKey.CALCIUM, eaten.calciumMg, needs.calciumMg, "mg"),
                need(MicroKey.B12, eaten.vitaminB12Ug, needs.vitaminB12Ug, "µg"),
                need(MicroKey.VITAMIN_D, eaten.vitaminDUg, needs.vitaminDUg, "µg"),
                need(MicroKey.VITAMIN_C, eaten.vitaminCMg, needs.vitaminCMg, "mg"),
                need(MicroKey.POTASSIUM, eaten.potassiumMg, needs.potassiumMg, "mg"),
                sodium,
            )
        }
    }
}

package com.example.mycalories.domain

/**
 * What the user typed in the food search, split into an amount and the words to search for:
 * "2 pan" → 2 × "pan", "half roti" → 0.5 × "roti", "paneer paratha" → 1 × "paneer paratha".
 */
data class TypedQuery(val quantity: Double, val term: String) {
    companion object {
        private val WORDS = mapOf(
            "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
            "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "half" to 0.5,
        )
        private val NUMBER = Regex("""^(\d+(?:\.\d+)?)?(½)?\s*[x×]?(?=\s|$)""")

        fun parse(text: String): TypedQuery {
            val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return TypedQuery(1.0, "")
            val first = words.first()
            val amount = WORDS[first] ?: NUMBER.find(first)?.takeIf { it.value.isNotBlank() }?.let { m ->
                (m.groupValues[1].toDoubleOrNull() ?: 0.0) + if (m.groupValues[2].isNotEmpty()) 0.5 else 0.0
            }
            var rest = if (amount != null) words.drop(1) else words
            if (amount != null && rest.firstOrNull() in setOf("x", "×")) rest = rest.drop(1)
            val qty = amount?.takeIf { it > 0 && it <= 100 } ?: 1.0
            return TypedQuery(qty, rest.joinToString(" "))
        }
    }
}

package org.octavius.data.util

/**
 * Utility for converting strings between different naming conventions.
 *
 * Supports conversions between:
 * - `snake_case` (both upper and lower)
 * - `camelCase`
 * - `PascalCase`
 *
 * Used internally for automatic mapping between PostgreSQL column names (snake_case)
 * and Kotlin property names (camelCase).
 */
object CaseConverter {

    /**
     * Converts a string from one naming convention to another.
     *
     * @param value The string to convert.
     * @param from The source naming convention.
     * @param to The target naming convention.
     * @return The converted string.
     */
    fun convert(value: String, from: CaseConvention, to: CaseConvention): String {
        if (from == to || value.isEmpty()) {
            return value
        }

        val words = splitToWords(value, from)
        return joinWords(words, to)
    }

    private fun splitToWords(value: String, convention: CaseConvention): List<String> {
        return when (convention) {
            CaseConvention.SNAKE_CASE_LOWER,
            CaseConvention.SNAKE_CASE_UPPER ->
                value.split('_').filter { it.isNotEmpty() }

            CaseConvention.PASCAL_CASE,
            CaseConvention.CAMEL_CASE -> {
                val words = mutableListOf<String>()
                var currentWord = StringBuilder()

                for (i in value.indices) {
                    val c = value[i]
                    val prev = if (i > 0) value[i - 1] else null
                    val next = if (i < value.length - 1) value[i + 1] else null

                    val isNewWord = if (prev != null) {
                        when {
                            // lowercase/digit followed by uppercase: user|Id, user1|Id
                            (prev.isLowerCase() || prev.isDigit()) && c.isUpperCase() -> true

                            // uppercase followed by uppercase+lowercase: UR|Le (acronym boundary like XML|Parser)
                            prev.isUpperCase() && c.isUpperCase() && next?.isLowerCase() == true -> true

                            // digit followed by letter: 1|user
                            prev.isDigit() && c.isLetter() -> true

                            else -> false
                        }
                    } else false

                    if (isNewWord) {
                        words.add(currentWord.toString())
                        currentWord = StringBuilder()
                    }
                    currentWord.append(c)
                }

                if (currentWord.isNotEmpty()) {
                    words.add(currentWord.toString())
                }
                words
            }
        }
    }

    private fun joinWords(words: List<String>, convention: CaseConvention): String {
        return when (convention) {
            CaseConvention.SNAKE_CASE_UPPER ->
                words.joinToString("_") { it.uppercase() }

            CaseConvention.SNAKE_CASE_LOWER ->
                words.joinToString("_") { it.lowercase() }

            CaseConvention.PASCAL_CASE ->
                words.joinToString("") { word ->
                    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

            CaseConvention.CAMEL_CASE ->
                words.mapIndexed { index, word ->
                    val lower = word.lowercase()
                    if (index == 0) {
                        lower
                    } else {
                        lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }.joinToString("")
        }
    }
}

// --- Extension Functions ---

/**
 * Converts any Camel/Pascal case text to snake_case.
 * Used e.g., to generate column names from field names (userId -> user_id).
 */
fun String.toSnakeCase(): String {
    // We assume Kotlin code is in CamelCase or PascalCase, and we want snake_case
    return CaseConverter.convert(this, CaseConvention.CAMEL_CASE, CaseConvention.SNAKE_CASE_LOWER)
}

/**
 * Converts snake_case to camelCase.
 * Used e.g., when mapping from database (user_id -> userId).
 */
fun String.toCamelCase(): String {
    return CaseConverter.convert(this, CaseConvention.SNAKE_CASE_LOWER, CaseConvention.CAMEL_CASE)
}

/**
 * Converts snake_case to PascalCase.
 * Used e.g., when mapping class names (table_name -> TableName).
 */
fun String.toPascalCase(): String {
    return CaseConverter.convert(this, CaseConvention.SNAKE_CASE_LOWER, CaseConvention.PASCAL_CASE)
}

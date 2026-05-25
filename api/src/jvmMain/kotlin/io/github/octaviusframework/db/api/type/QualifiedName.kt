package io.github.octaviusframework.db.api.type

import io.github.octaviusframework.db.api.exception.BadStatementException
import io.github.octaviusframework.db.api.exception.BadStatementExceptionMessage

/**
 * Represents a qualified PostgreSQL name (schema + object name).
 * Handles quoting correctly even if names contain dots.
 */
data class QualifiedName(
    val schema: String,
    val name: String,
    val isArray: Boolean = false
) {
    override fun toString(): String {
        val base = if (schema.isBlank()) name else "$schema.$name"
        return if (isArray) "$base[]" else base
    }

    companion object {
        /**
         * Escapes a PostgreSQL identifier (e.g. table name, type name) by wrapping it in double quotes
         * and escaping any internal double quotes if it contains special characters.
         */
        fun quoteIdentifier(value: String): String {
            if (value.isBlank()) return ""
            // According to PostgreSQL rules, unquoted identifiers must start with a letter or underscore,
            // and can contain letters, underscores, digits, or dollar signs.
            // If it starts with a digit, or contains any other character (dots, spaces, quotes, dashes, etc.),
            // it MUST be quoted to be handled correctly as a single identifier.
            var shouldQuote = value[0].isDigit()
            for (c in value) {
                if (c == '\u0000') {
                    throw BadStatementException(
                        BadStatementExceptionMessage.SYNTAX_ERROR,
                        cause = IllegalArgumentException("PostgreSQL identifiers cannot contain the NUL (\\0) character.")
                    )
                }
                if (!shouldQuote && !(c.isLetter() || c == '_' || c == '$' || c.isDigit())) {
                    shouldQuote = true
                }
            }

            if (shouldQuote) {
                return buildString(value.length + 2) {
                    append('"')
                    for (c in value) {
                        if (c == '"') append('"')
                        append(c)
                    }
                    append('"')
                }
            }

            // Otherwise, we don't add quotes "artificially" to stay explicit and allow PG folding.
            return value
        }
    }

    /**
     * Returns a SQL-safe representation. 
     * Respects existing quotes and adds new ones only if necessary (e.g. dots in name).
     */
    fun quote(): String {
        val quotedBase = if (schema.isBlank()) {
            quoteIdentifier(name)
        } else {
            "${quoteIdentifier(schema)}.${quoteIdentifier(name)}"
        }
        return if (isArray) "$quotedBase[]" else quotedBase
    }

    fun asArray(): QualifiedName = copy(isArray = true)
}

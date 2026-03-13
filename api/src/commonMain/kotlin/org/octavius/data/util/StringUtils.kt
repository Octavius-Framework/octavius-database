package org.octavius.data.util


/**
 * Simple function cleaning String from typographic apostrophes and quotes
 */
fun String.clean(): String {
    return buildString(this.length) {
        for (c in this@clean) {
            when (c) {
                '‘', '’' -> append('\'')
                '“', '”' -> append('"')
                else    -> append(c)
            }
        }
    }
}

/**
 * Escapes a PostgreSQL identifier (e.g. table name, type name) by wrapping it in double quotes
 * and escaping any internal double quotes.
 */
fun String.quoteIdentifier(): String {
    return buildString(this.length + 2) {
        append('"')
        for (c in this@quoteIdentifier) {
            if (c == '"') append('"')
            append(c)
        }
        append('"')
    }
}

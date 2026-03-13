package org.octavius.data.type

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

    /**
     * Escapes a PostgreSQL identifier (e.g. table name, type name) by wrapping it in double quotes
     * and escaping any internal double quotes if it contains special characters.
     */
    private fun String.quoteIdentifier(): String {
        if (this.isBlank()) return ""
        // If already quoted, we assume it's correctly escaped and return as is.
        if (this.startsWith('"') && this.endsWith('"')) return this
        
        // If it contains dots or quotes, we MUST quote it to preserve it as a single part.
        if ("." in this || "\"" in this) {
            return buildString(this.length + 2) {
                append('"')
                for (c in this@quoteIdentifier) {
                    if (c == '"') append('"')
                    append(c)
                }
                append('"')
            }
        }
        
        // Otherwise, we don't add quotes "artificially" to stay explicit and allow PG folding.
        return this
    }

    /**
     * Returns a SQL-safe representation. 
     * Respects existing quotes and adds new ones only if necessary (e.g. dots in name).
     */
    fun quote(): String {
        val quotedBase = if (schema.isBlank()) {
            name.quoteIdentifier()
        } else {
            "${schema.quoteIdentifier()}.${name.quoteIdentifier()}"
        }
        return if (isArray) "$quotedBase[]" else quotedBase
    }

    fun asArray(): QualifiedName = copy(isArray = true)

    companion object {
        fun from(fullName: String): QualifiedName {
            val isArray = fullName.endsWith("[]")
            val cleanName = if (isArray) fullName.dropLast(2) else fullName

            val parts = mutableListOf<String>()
            val currentPart = StringBuilder()
            var inQuotes = false
            
            var i = 0
            while (i < cleanName.length) {
                val c = cleanName[i]
                when {
                    c == '"' -> {
                        if (inQuotes && i + 1 < cleanName.length && cleanName[i + 1] == '"') {
                            // Escaped quote
                            currentPart.append("\"\"")
                            i++ 
                        } else {
                            inQuotes = !inQuotes
                            currentPart.append(c)
                        }
                    }
                    c == '.' && !inQuotes -> {
                        parts.add(currentPart.toString())
                        currentPart.setLength(0)
                    }
                    else -> currentPart.append(c)
                }
                i++
            }
            parts.add(currentPart.toString())

            return when (parts.size) {
                1 -> QualifiedName("", parts[0], isArray)
                2 -> QualifiedName(parts[0], parts[1], isArray)
                else -> throw IllegalArgumentException("Invalid qualified name: $fullName. Expected at most one dot outside of quotes.")
            }
        }
    }
}

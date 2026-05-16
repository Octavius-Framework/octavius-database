package io.github.octaviusframework.db.core.type

/** Represents a named parameter found in SQL with its position. */
internal data class ParsedParameter(val name: String, val startIndex: Int, val endIndex: Int)


/**
 * A specialized SQL preprocessor for PostgreSQL that handles named parameter extraction
 * and JDBC-compatible transformations while respecting PostgreSQL's lexical rules.
 *
 * This component is designed to correctly identify boundaries of various SQL constructs
 * (literals, comments, dollar-quotes) to ensure that transformations are only applied
 * to actual SQL code and not inside strings or comments.
 *
 * ### Key Responsibilities:
 * 1. **Named Parameter Extraction**: Identifies `@param` style parameters. The `@` prefix
 *    is used specifically to avoid conflicts with PostgreSQL's `:` operator (used for type
 *    casting `::` and standard array range syntax).
 * 2. **Operator Escaping**: Converts raw `?` operators (common in PostgreSQL JSONB
 *    operations like `?`, `?|`, `?&`) into `??` to prevent JDBC from misinterpreting
 *    them as positional parameter placeholders.
 *
 * ### Lexical Awareness:
 * The preprocessor correctly ignores content within:
 * - **Single-line comments**: `-- ...`
 * - **Multi-line comments**: `/* ... */` (supports nested comments)
 * - **Standard string literals**: `'...'` (handles doubled single quotes)
 * - **Escape string literals**: `E'...'` or `e'...'` (properly handles backslash escapes)
 * - **Quoted identifiers**: `"..."`
 * - **Dollar-quoted strings**: `$$...$$` or `$tag$...$tag$`
 *
 * Implementation is partially inspired by Spring's `NamedParameterUtils` but significantly
 * extended to support PostgreSQL-specific syntax like dollar-quoting and nested comments.
 */
internal object PostgresSqlPreprocessor {

    private const val PARAMETER_SEPARATORS = "\"':&,;()|=+-*%/\\<>^[]@~!#`?"
    private val separatorIndex = BooleanArray(128).apply {
        PARAMETER_SEPARATORS.forEach { this[it.code] = true }
    }

    private fun isParameterSeparator(c: Char): Boolean {
        return (c.code < 128 && separatorIndex[c.code]) || c.isWhitespace()
    }

    /**
     * Analyzes the given SQL string and returns a list of found parameters in order of occurrence.
     *
     * Only identifies parameters starting with '@'.
     * Correcty skips content inside comments, string literals, and dollar-quoted strings.
     */
    fun parse(sql: String): List<ParsedParameter> {
        val foundParameters = mutableListOf<ParsedParameter>()
        var i = 0

        while (i < sql.length) {
            val skipIndex = findConstructEnd(sql, i)
            if (skipIndex > i) {
                i = skipIndex + 1
                continue
            }

            if (sql[i] == '@') {
                i = processAt(sql, i, foundParameters)
            }
            i++
        }
        return foundParameters
    }

    /**
     * Escapes literal question marks (?) in the SQL by replacing them with (??).
     *
     * This is necessary because JDBC uses '?' as a positional parameter placeholder,
     * while PostgreSQL uses '?', '?|', '?&' as operators for JSONB.
     *
     * This method respects PostgreSQL quoting and comment rules, only escaping
     * question marks that are not inside strings or comments.
     */
    fun escapeQuestionMarks(sql: String): String {
        val result = StringBuilder(sql.length + 10)
        var i = 0

        while (i < sql.length) {
            val skipIndex = findConstructEnd(sql, i)
            if (skipIndex > i) {
                result.append(sql, i, skipIndex + 1)
                i = skipIndex + 1
                continue
            }

            val currentChar = sql[i]
            if (currentChar == '?') {
                result.append("??")
            } else {
                result.append(currentChar)
            }
            i++
        }
        return result.toString()
    }

    /**
     * Checks if the character at the current index starts a special SQL construct
     * (string literal, comment, dollar-quote) and returns the index of its last character.
     * If not a construct start, returns the original index.
     */
    private fun findConstructEnd(sql: String, i: Int): Int {
        return when (sql[i]) {
            '\'' -> processSingleQuote(sql, i)
            '"' -> skipUntil(sql, i, '"')
            '-' -> if (i + 1 < sql.length && sql[i + 1] == '-') skipUntil(sql, i, '\n') else i
            '/' -> if (i + 1 < sql.length && sql[i + 1] == '*') skipComment(sql, i) else i
            '$' -> {
                val end = findDollarQuoteEnd(sql, i)
                if (end != -1) end else i
            }
            else -> i
        }
    }

    /**
     * Handles string literals, including PostgreSQL Escape strings (E'...')
     * Returns the index of the closing quote.
     */
    private fun processSingleQuote(sql: String, index: Int): Int {
        // Check if this is an E'...' escape string literal
        // We look behind to see if the previous char was 'E' or 'e'
        return if (index > 0 && (sql[index - 1] == 'E' || sql[index - 1] == 'e')) {
            skipBackslashEscapedLiteral(sql, index)
        } else {
            skipUntil(sql, index, '\'')
        }
    }

    /**
     * Handles potential named parameters (@param).
     * If a parameter is found, it is added to the list.
     * Returns the index of the last character of the processed token.
     */
    private fun processAt(
        sql: String,
        index: Int,
        foundParameters: MutableList<ParsedParameter>
    ): Int {
        // Parse named parameter
        var j = index + 1
        while (j < sql.length && !isParameterSeparator(sql[j])) {
            j++
        }

        if (j - index > 1) { // Found parameter name (longer than 0 characters)
            val paramName = sql.substring(index + 1, j)
            foundParameters.add(ParsedParameter(paramName, index, j))
            return j - 1 // Return index of the last character of the parameter
        }

        return index
    }

    /** Skips to the end of a dollar-quoted block. Returns the index of the last character. */
    private fun findDollarQuoteEnd(sql: String, start: Int): Int {
        if (start + 1 >= sql.length) return -1

        // 1. Find the opening tag (e.g., $tag$)
        // Tag must follow unquoted identifier rules: [a-zA-Z_][a-zA-Z0-9_]* (no dollar signs)
        var tagEnd = start
        while (tagEnd + 1 < sql.length && sql[tagEnd + 1] != '$') {
            val char = sql[tagEnd + 1]
            // Validate tag character according to PostgreSQL unquoted identifier rules
            if (!isValidTagCharacter(char, isFirstChar = tagEnd == start)) {
                return -1 // Invalid tag character, not a valid dollar-quote
            }

            tagEnd++
        }

        if (tagEnd + 1 >= sql.length || sql[tagEnd + 1] != '$') {
            return -1 // Complete opening tag not found
        }

        val tagLength = (tagEnd + 1) - start + 1

        // 2. Search for the closing tag
        var searchPos = tagEnd + 2
        while (searchPos + tagLength <= sql.length) {
            if (sql.regionMatches(searchPos, sql, start, tagLength)) {
                return searchPos + tagLength - 1
            }
            searchPos++
        }

        return -1 // Closing tag not found
    }

    /**
     * Checks if a character is valid for a dollar-quote tag.
     * Tags follow PostgreSQL unquoted identifier rules:
     * - First character: [a-zA-Z_]
     * - Subsequent characters: [a-zA-Z0-9_]
     * - Dollar signs are NOT allowed in tags
     */
    private fun isValidTagCharacter(char: Char, isFirstChar: Boolean): Boolean {
        return when {
            char.isLetter() || char == '_' -> true
            !isFirstChar && char in '0'..'9' -> true
            else -> false
        }
    }

    private fun skipBackslashEscapedLiteral(sql: String, start: Int): Int {
        var i = start + 1
        while (i < sql.length) {
            // This is an escape character, ignore the next character
            if (sql[i] == '\\') {
                i++
            } else if (sql[i] == '\'') {
                // This is the end of the literal
                return i
            }
            i++
        }
        return i
    }

    /** Skips to the next occurrence of the specified character. Returns its index. */
    private fun skipUntil(sql: String, start: Int, endChar: Char): Int {
        val index = sql.indexOf(endChar, start + 1)
        return if (index == -1) sql.length else index
    }

    /** Skips comment. */
    private fun skipComment(sql: String, start: Int): Int {
        var i = start + 2 // skip initial /*
        var depth = 1
        while (i < sql.length && depth > 0) {
            if (i + 1 < sql.length) {
                if (sql[i] == '/' && sql[i + 1] == '*') {
                    depth++
                    i++
                } else if (sql[i] == '*' && sql[i + 1] == '/') {
                    depth--
                    i++
                }
            }
            i++
        }
        return i - 1
    }
}
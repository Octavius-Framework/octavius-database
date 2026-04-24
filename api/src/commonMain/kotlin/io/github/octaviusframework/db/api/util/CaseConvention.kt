package io.github.octaviusframework.db.api.util

/**
 * Supported naming conventions for automatic string conversion.
 *
 * These conventions are primarily used during:
 * 1. Mapping between Kotlin property names (`camelCase`) and PostgreSQL column names (`snake_case`).
 * 2. Mapping between Kotlin Enum constants and their string representation in PostgreSQL.
 * 3. Automatic generation of PostgreSQL type names from Kotlin class names.
 */
enum class CaseConvention {
    /**
     * Uppercase words separated by underscores.
     * Example: `MY_ENUM_VALUE`
     */
    SNAKE_CASE_UPPER,

    /**
     * Lowercase words separated by underscores.
     * Example: `my_table_name`
     */
    SNAKE_CASE_LOWER,

    /**
     * Words concatenated together, each starting with an uppercase letter.
     * Example: `MyDataClass`
     */
    PASCAL_CASE,

    /**
     * Words concatenated together, each except the first starting with an uppercase letter.
     * Example: `myProperty`
     */
    CAMEL_CASE,
}
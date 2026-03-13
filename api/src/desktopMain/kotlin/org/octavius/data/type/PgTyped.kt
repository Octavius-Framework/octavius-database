package org.octavius.data.type

/**
 * Wraps a value to explicitly specify the target PostgreSQL type.
 *
 * Causes addition of a type cast (`::pgType`) to the generated SQL fragment.
 * Useful for handling type ambiguities, e.g., with arrays.
 *
 * @param value Value to embed in the query (avoid using with data classes where this is added automatically!).
 * @param pgType PostgreSQL type name to which the value should be cast.
 */
data class PgTyped(val value: Any?, val pgType: QualifiedName) {
    /**
     * Compatibility constructor for String type names.
     */
    constructor(value: Any?, pgType: String) : this(value, QualifiedName.from(pgType))
}


/**
 * Wraps a value in PgTyped to explicitly specify the target PostgreSQL type
 * in a type-safe manner.
 */
fun Any?.withPgType(pgType: PgStandardType): PgTyped = 
    PgTyped(this, QualifiedName("", pgType.typeName))

/**
 * Wraps a value in PgTyped in a more fluid way.
 * Use this method only for custom or rare types that
 * are not defined in `PgStandardType`.
 *
 * @param pgType Full type name (e.g. "public.my_type" or "text[]").
 * @see PgStandardType
 */
fun Any?.withPgType(pgType: String): PgTyped = PgTyped(this, pgType)

/**
 * Wraps a value in PgTyped with explicit schema and name.
 * 
 * @param name Type name.
 * @param schema Schema name.
 */
fun Any?.withPgType(name: String, schema: String): PgTyped = 
    PgTyped(this, QualifiedName(schema, name))

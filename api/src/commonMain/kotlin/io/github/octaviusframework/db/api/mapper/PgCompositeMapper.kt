package io.github.octaviusframework.db.api.mapper

/**
 * Interface for manual (non-reflective) mapping of a data class to/from a Map.
 * Used for PostgreSQL composite types to bypass reflection-based mapping and improve performance.
 *
 * Inherits from [DataMapper] for one-way mapping (Map -> Object) and adds
 * [toDataMap] for the reverse direction (Object -> Map) required for database writes.
 */
interface PgCompositeMapper<T : Any> : DataMapper<T> {
    /**
     * Converts the provided object [obj] to a Map.
     * The map keys should match the PostgreSQL composite attribute names.
     */
    fun toDataMap(obj: T): Map<String, Any?>
}

/**
 * Internal marker to indicate that no explicit mapper is provided.
 */
object DefaultPgCompositeMapper : PgCompositeMapper<Any> {
    override fun toDataObject(map: Map<String, Any?>): Any = throw UnsupportedOperationException()
    override fun toDataMap(obj: Any): Map<String, Any?> = throw UnsupportedOperationException()
}

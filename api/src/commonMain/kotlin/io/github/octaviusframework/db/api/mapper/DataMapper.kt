package io.github.octaviusframework.db.api.mapper

/**
 * Interface for manual mapping of data from a Map to a data object.
 *
 * This is the base interface for all manual mappers in Octavius.
 * It is a functional interface, allowing for simple lambda usage:
 * ```kotlin
 * .mapTo { map -> User(map["name"] as String) }
 * ```
 */
fun interface DataMapper<T : Any> {
    /**
     * Creates an instance of [T] from the provided [map].
     * The map keys are the attribute names (typically in snake_case as they come from DB).
     */
    fun toDataObject(map: Map<String, Any?>): T
}

package io.github.octaviusframework.db.api.builder

import io.github.octaviusframework.db.api.annotation.PgCompositeMapper
import io.github.octaviusframework.db.api.type.QualifiedName
import io.github.octaviusframework.db.api.type.TypeHandler

/**
 * Configuration options for a single database query.
 *
 * Allows for overriding global settings like type handlers, and composite mapping
 * for a specific query execution.
 */
data class QueryOptions(
    val typeHandlers: List<TypeHandler<*>> = emptyList(),
    val compositeAsMapTypes: Set<QualifiedName> = emptySet(),
    val customCompositeMappers: Map<QualifiedName, PgCompositeMapper<*>> = emptyMap(),
    val returnAllCompositesAsMaps: Boolean = false
)

/**
 * Builder for [QueryOptions], providing a DSL for configuration.
 */
interface QueryOptionsBuilder {
    /**
     * Registers a custom [TypeHandler] for use within this query.
     * This handler will take precedence over globally registered handlers.
     */
    fun registerTypeHandler(handler: TypeHandler<*>): QueryOptionsBuilder

    /**
     * Registers a custom [PgCompositeMapper] for a specific PostgreSQL composite type.
     * Useful when you want to map a composite type to a class differently than the default
     * or bypass reflection for a specific query.
     *
     * @param name Name of the composite type.
     * @param schema Schema of the composite type (defaults to empty, meaning search_path will be used).
     * @param mapper The mapper instance to use.
     */
    fun registerCompositeMapper(name: String, schema: String = "", mapper: PgCompositeMapper<*>): QueryOptionsBuilder

    /**
     * Directs Octavius to return a specific composite type as a nested `Map<String, Any?>`
     * instead of trying to map it to a Kotlin class.
     *
     * @param name Name of the composite type.
     * @param schema Schema of the composite type (defaults to empty, meaning search_path will be used).
     */
    fun returnCompositeAsMap(name: String, schema: String = ""): QueryOptionsBuilder

    /**
     * Directs Octavius to return ALL composite types encountered in this query
     * as nested `Map<String, Any?>` instead of mapping them to Kotlin classes.
     * Useful for dynamic reporting or handling arbitrary queries.
     */
    fun returnAllCompositesAsMaps(): QueryOptionsBuilder
}

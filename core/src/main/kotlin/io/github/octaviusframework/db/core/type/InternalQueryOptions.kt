package io.github.octaviusframework.db.core.type

import io.github.octaviusframework.db.api.builder.QueryOptions
import io.github.octaviusframework.db.api.type.TypeHandler
import io.github.octaviusframework.db.core.type.registry.TypeRegistry

/**
 * Internal wrapper for [QueryOptions] that pre-resolves OIDs for custom [TypeHandler]s.
 *
 * This class ensures that expensive OID lookups for per-query custom handlers
 * are performed once per query execution (via [customHandlersByOid] map)
 * instead of per-row or per-column.
 */
internal class InternalQueryOptions(
    val options: QueryOptions,
    private val typeRegistry: TypeRegistry
) {
    /**
     * Maps PostgreSQL OID to a query-specific custom [TypeHandler].
     * Computed immediately upon creation.
     */
    val customHandlersByOid: Map<Int, TypeHandler<*>> = if (options.typeHandlers.isEmpty()) {
        emptyMap()
    } else {
        val map = mutableMapOf<Int, TypeHandler<*>>()
        for (handler in options.typeHandlers) {
            val (oid, _) = typeRegistry.resolveOid(handler.pgTypeName, handler.pgSchema)
            // If multiple handlers for same OID, keep the first one (matching previous 'find' behavior)
            if (!map.containsKey(oid)) {
                map[oid] = handler
            }
        }
        map
    }

    companion object {
        /**
         * Creates an empty [InternalQueryOptions] for tests or scenarios where no options are provided.
         */
        fun empty(typeRegistry: TypeRegistry) = InternalQueryOptions(QueryOptions(), typeRegistry)
    }
}

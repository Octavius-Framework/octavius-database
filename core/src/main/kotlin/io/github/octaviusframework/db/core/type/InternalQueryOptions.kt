package io.github.octaviusframework.db.core.type

import io.github.octaviusframework.db.api.builder.QueryOptions
import io.github.octaviusframework.db.api.exception.TypeRegistryException
import io.github.octaviusframework.db.api.exception.TypeRegistryExceptionMessage
import io.github.octaviusframework.db.api.type.TypeHandler
import io.github.octaviusframework.db.core.type.registry.TypeRegistry
import kotlin.reflect.KClass

/**
 * Internal wrapper for [QueryOptions] that pre-resolves OIDs for custom [TypeHandler]s.
 *
 * This class ensures that expensive OID lookups for per-query custom handlers
 * are performed once per query execution (via [customHandlersByOid] map)
 * instead of per-row or per-column.
 */
internal class InternalQueryOptions(
    val options: QueryOptions,
    typeRegistry: TypeRegistry
) {
    /**
     * Maps PostgreSQL OID to a query-specific custom [TypeHandler].
     * Computed immediately upon creation.
     */
    val customHandlersByOid: Map<Int, TypeHandler<*>>
    
    /**
     * Maps Kotlin class to a query-specific custom [TypeHandler].
     * Computed immediately upon creation.
     */
    val customHandlersByClass: Map<KClass<*>, TypeHandler<*>>

    init {
        if (options.typeHandlers.isEmpty()) {
            customHandlersByOid = emptyMap()
            customHandlersByClass = emptyMap()
        } else {
            val oidMap = mutableMapOf<Int, TypeHandler<*>>()
            val classMap = mutableMapOf<KClass<*>, TypeHandler<*>>()
            
            for (handler in options.typeHandlers) {
                // 1. OID Mapping & Validation
                val (oid, qualifiedName) = typeRegistry.resolveOid(handler.pgTypeName, handler.pgSchema)
                if (oidMap.containsKey(oid)) {
                    throw TypeRegistryException(
                        messageEnum = TypeRegistryExceptionMessage.AMBIGUOUS_TYPE_MAPPING,
                        typeName = qualifiedName.toString(),
                        details = "Multiple custom type handlers registered for type '$qualifiedName' (OID: $oid) in QueryOptions."
                    )
                }
                oidMap[oid] = handler
                
                // 2. Class Mapping & Validation
                if (classMap.containsKey(handler.kotlinClass)) {
                    throw TypeRegistryException(
                        messageEnum = TypeRegistryExceptionMessage.AMBIGUOUS_TYPE_MAPPING,
                        typeName = handler.kotlinClass.simpleName ?: "unknown",
                        details = "Multiple custom type handlers registered for Kotlin class '${handler.kotlinClass.simpleName}' in QueryOptions."
                    )
                }
                classMap[handler.kotlinClass] = handler
            }
            customHandlersByOid = oidMap
            customHandlersByClass = classMap
        }
    }


    companion object {
        /**
         * Creates an empty [InternalQueryOptions] for tests or scenarios where no options are provided.
         */
        fun empty(typeRegistry: TypeRegistry) = InternalQueryOptions(QueryOptions(), typeRegistry)
    }
}

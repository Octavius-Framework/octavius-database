package io.github.octaviusframework.db.core.type.registry

import io.github.octaviusframework.db.api.mapper.PgCompositeMapper
import io.github.octaviusframework.db.api.type.QualifiedName
import kotlin.reflect.KClass

// --- Data Models ---

/** Classification of PostgreSQL types for routing to appropriate converters. */
internal enum class TypeCategory {
    STANDARD, ENUM, COMPOSITE, ARRAY, DYNAMIC_DTO, DYNAMIC_MAP
}

/** Metadata for a PostgreSQL ENUM type, enabling bidirectional value mapping. */
internal data class PgEnumDefinition(
    val oid: Int,
    val typeName: QualifiedName,
    val valueToEnumMap: Map<String, Enum<*>>,
    val kClass: KClass<out Enum<*>>
) {
    val enumToValueMap: Map<Enum<*>, String> = valueToEnumMap.map { it.value to it.key }.toMap()
}

/** Metadata for a PostgreSQL COMPOSITE type with ordered attribute definitions. */
internal data class PgCompositeDefinition(
    val oid: Int,
    val typeName: QualifiedName,
    val attributes: Map<String, Int>, // colName -> colOid (ordered)
    val kClass: KClass<*>,
    val mapper: PgCompositeMapper<Any>? = null
) {
    val dbAttributes = attributes.toList()
}

/** Metadata for a PostgreSQL ARRAY type, linking to its element type. */
internal data class PgArrayDefinition(
    val oid: Int,
    val typeName: QualifiedName,
    val elementOid: Int
)

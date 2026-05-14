package io.github.octaviusframework.db.core.type.registry

import io.github.octaviusframework.db.api.exception.TypeRegistryException
import io.github.octaviusframework.db.api.exception.TypeRegistryExceptionMessage
import io.github.octaviusframework.db.api.type.QualifiedName
import io.github.octaviusframework.db.api.type.TypeHandler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Central repository of PostgreSQL type metadata for bidirectional conversion.
 *
 * Provides lookup methods for:
 * - **Reading (DB → Kotlin)**: Category routing, enum/composite/array definitions by OID.
 * - **Writing (Kotlin → DB)**: Class to PostgreSQL type name mapping and OID discovery.
 * - **Dynamic DTOs**: Serializer lookup for `@DynamicallyMappable` types.
 *
 * Populated at startup by [TypeRegistryLoader].
 */
internal class TypeRegistry(
    // Main router: OID -> Category
    private val oidCategoryMap: Map<Int, TypeCategory>,
    // Specialized detail maps by OID
    private val enumsByOid: Map<Int, PgEnumDefinition>,
    private val compositesByOid: Map<Int, PgCompositeDefinition>,
    private val arraysByOid: Map<Int, PgArrayDefinition>,
    // Specialized handlers (mostly for standard types)
    private val handlersByOid: Map<Int, TypeHandler<*>>,
    private val handlersByClass: Map<KClass<*>, TypeHandler<*>>,
    // Mappings for writing (Kotlin Class -> PgType)
    private val classToPgNameMap: Map<KClass<*>, QualifiedName>,
    // Dynamic mappings (Dynamic Key -> Kotlin Class)
    private val dynamicSerializers: Map<String, KSerializer<Any>>,
    private val classToDynamicNameMap: Map<KClass<*>, String>,
    // Reverse maps for name-based lookup
    private val pgNameToOidMap: Map<QualifiedName, Int>,
    // Human-readable names for OIDs (for error reporting)
    private val oidToNameMap: Map<Int, QualifiedName>,
    // Resolution data for name-based lookup with search_path
    private val searchPath: List<String>,
    private val nameToSchemaOid: Map<String, Map<String, Int>>
) {
    // --- READING (DB -> Kotlin) ---

    fun getCategory(oid: Int): TypeCategory =
        oidCategoryMap[oid] ?: throwNotFound(oid)

    fun getEnumDefinition(oid: Int): PgEnumDefinition =
        enumsByOid[oid] ?: throwNotFound(oid, "ENUM")

    fun getCompositeDefinition(oid: Int): PgCompositeDefinition =
        compositesByOid[oid] ?: throwNotFound(oid, "COMPOSITE")

    fun getArrayDefinition(oid: Int): PgArrayDefinition =
        arraysByOid[oid] ?: throwNotFound(oid, "ARRAY")

    fun getDynamicSerializer(dynamicTypeName: String): KSerializer<Any> =
        dynamicSerializers[dynamicTypeName] ?: throw TypeRegistryException(
            TypeRegistryExceptionMessage.DYNAMIC_TYPE_NOT_FOUND,
            typeName = dynamicTypeName,
            expectedCategory = "DYNAMIC"
        )

    fun getHandlerByOid(oid: Int): TypeHandler<*>? = handlersByOid[oid]

    fun getHandlerByClass(kClass: KClass<*>): TypeHandler<*>? {
        handlersByClass[kClass]?.let { return it }
        // Json Element - it is superclass
        if (kClass.isSubclassOf(JsonElement::class)) {
            return handlersByClass[JsonElement::class]
        }
        return null
    }

    // --- WRITING (Kotlin -> DB) ---

    fun getPgTypeNameForClass(clazz: KClass<*>): QualifiedName =
        classToPgNameMap[clazz] ?: throw TypeRegistryException(
            TypeRegistryExceptionMessage.KOTLIN_CLASS_NOT_MAPPED,
            typeName = clazz.qualifiedName ?: clazz.simpleName ?: "unknown"
        )

    fun getDynamicTypeNameForClass(clazz: KClass<*>): String? = 
        classToDynamicNameMap[clazz]

    fun getOidForName(name: QualifiedName): Int =
        pgNameToOidMap[name] ?: throw TypeRegistryException(
            TypeRegistryExceptionMessage.PG_TYPE_NOT_FOUND,
            typeName = name.toString()
        )

    fun isPgType(kClass: KClass<*>): Boolean = 
        classToPgNameMap.containsKey(kClass)

    /**
     * Resolves a PostgreSQL type name to its OID and fully qualified name.
     * Considers explicit schema and search_path.
     */
    fun resolveOid(
        typeName: String,
        requestedSchema: String = ""
    ): Pair<Int, QualifiedName> = resolveOid(typeName, requestedSchema, searchPath, nameToSchemaOid)

    // --- HELPERS ---

    private fun throwNotFound(oid: Int, expected: String? = null): Nothing {
        val typeName = oidToNameMap[oid]?.toString() ?: "unknown"
        throw TypeRegistryException(
            messageEnum = TypeRegistryExceptionMessage.PG_TYPE_NOT_FOUND,
            typeName = typeName,
            oid = oid,
            expectedCategory = expected
        )
    }

    companion object {
        /**
         * Core OID resolution logic, shared between [TypeRegistry] and [TypeRegistryLoader].
         */
        fun resolveOid(
            typeName: String,
            requestedSchema: String,
            searchPath: List<String>,
            nameToSchemaOid: Map<String, Map<String, Int>>
        ): Pair<Int, QualifiedName> {
            val schemasForName = nameToSchemaOid[typeName]
                ?: throw TypeRegistryException(
                    messageEnum = TypeRegistryExceptionMessage.TYPE_DEFINITION_MISSING_IN_DB,
                    typeName = typeName,
                    details = "Type '$typeName' not found in any scanned schemas"
                )

            // 1. If schema is explicitly requested
            if (requestedSchema.isNotBlank()) {
                val oid = schemasForName[requestedSchema]
                    ?: throw TypeRegistryException(
                        messageEnum = TypeRegistryExceptionMessage.TYPE_DEFINITION_MISSING_IN_DB,
                        typeName = typeName,
                        details = "Type '$typeName' not found in requested schema '$requestedSchema'"
                    )
                return oid to QualifiedName(requestedSchema, typeName)
            }

            // 2. If schema is empty, look in search_path (first match wins)
            for (schema in searchPath) {
                schemasForName[schema]?.let { oid -> return oid to QualifiedName(schema, typeName) }
            }

            // 3. If not in search_path, check for unambiguous match
            return when (schemasForName.size) {
                1 -> {
                    val (schema, oid) = schemasForName.entries.first()
                    oid to QualifiedName(schema, typeName)
                }

                else -> throw TypeRegistryException(
                    messageEnum = TypeRegistryExceptionMessage.TYPE_DEFINITION_MISSING_IN_DB,
                    typeName = typeName,
                    details = "Type '$typeName' is ambiguous. Found in schemas: ${schemasForName.keys.joinToString()}. Please specify schema."
                )
            }
        }
    }
}


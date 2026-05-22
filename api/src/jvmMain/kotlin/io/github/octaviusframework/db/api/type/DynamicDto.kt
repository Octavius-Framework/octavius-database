package io.github.octaviusframework.db.api.type

import io.github.octaviusframework.db.api.annotation.DynamicallyMappable
import io.github.octaviusframework.db.api.annotation.PgComposite
import io.github.octaviusframework.db.api.exception.TypeMappingException
import io.github.octaviusframework.db.api.exception.TypeMappingExceptionMessage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Represents a polymorphic object for database storage, mapping to the `dynamic_dto` PostgreSQL type.
 *
 * `DynamicDto` acts as a "transport container" that bundles a type name and a JSON payload.
 * This allows PostgreSQL to store and query polymorphic data structures without requiring
 * dedicated `COMPOSITE` types. Corresponds to the `dynamic_dto` type in the database.
 *
 * ### Asymmetric Data Flow
 * - **Writing (Kotlin -> DB)**: Wrap your domain object in a `DynamicDto` using `dataAccess.toDynamicDto(obj)`.
 *   The framework converts this into the database's `dynamic_dto(text, jsonb)` structure.
 * - **Reading (DB -> Kotlin)**: The framework automatically unmarshals `dynamic_dto` values
 *   directly into your domain classes (annotated with [DynamicallyMappable]).
 *
 * ### Example: Writing Polymorphic Data
 * ```kotlin
 * // A legionnaire's benefit can be either a land grant or a military pension —
 * // both stored in the same 'veteran_benefit' column.
 * val grant = LandGrant(province = "Gallia Narbonensis", areraActa = BigDecimal("120.5"))
 * val dto = dataAccess.toDynamicDto(grant)
 * dataAccess.insertInto("veterans").values("id" to 1, "benefit" to dto).execute()
 * ```
 *
 * @property typeName Identifier linked to a [DynamicallyMappable] class.
 * @property dataPayload The serialized state of the object as a [JsonElement].
 */
@ConsistentCopyVisibility
@PgComposite(name = "dynamic_dto", schema = "public")
data class DynamicDto private constructor(
    val typeName: String,
    val dataPayload: JsonElement
) {
    companion object {
        /**
         * [FRAMEWORK PATH]
         * Creates DTO using an externally provided (cached) serializer and specific Json instance.
         * Zero reflection, maximum performance.
         * 
         * Application code should use `DataAccess.toDynamicDto` instead.
         */
        fun from(value: Any, typeName: String, serializer: KSerializer<Any>, json: Json): DynamicDto {
            try {
                // Serialization to JsonElement
                val jsonPayload = json.encodeToJsonElement(serializer, value)

                return DynamicDto(typeName, jsonPayload)
            } catch (e: Exception) {
                throw TypeMappingException(
                    messageEnum = TypeMappingExceptionMessage.JSON_SERIALIZATION_FAILED,
                    targetType = typeName,
                    cause = e
                )
            }
        }
    }
}
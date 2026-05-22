package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.mapper.PgCompositeMapper
import io.github.octaviusframework.db.api.builder.QueryOptions
import io.github.octaviusframework.db.api.builder.QueryOptionsBuilder
import io.github.octaviusframework.db.api.type.QualifiedName
import io.github.octaviusframework.db.api.type.TypeHandler
import kotlinx.serialization.json.Json

internal class DatabaseQueryOptionsBuilder : QueryOptionsBuilder {

    private val typeHandlers = mutableListOf<TypeHandler<*>>()
    private val compositeAsMapTypes = mutableSetOf<QualifiedName>()
    private val customCompositeMappers = mutableMapOf<QualifiedName, PgCompositeMapper<*>>()

    private var returnAllCompositesAsMaps = false
    private var json: Json? = null

    override fun registerTypeHandler(handler: TypeHandler<*>): QueryOptionsBuilder = apply {
        typeHandlers.add(handler)
    }

    override fun registerCompositeMapper(
        name: String,
        schema: String,
        mapper: PgCompositeMapper<*>
    ): QueryOptionsBuilder = apply {
        customCompositeMappers[QualifiedName(schema, name)] = mapper
    }

    override fun returnCompositeAsMap(name: String, schema: String): QueryOptionsBuilder = apply {
        compositeAsMapTypes.add(QualifiedName(schema, name))
    }

    override fun returnAllCompositesAsMaps(): QueryOptionsBuilder = apply {
        returnAllCompositesAsMaps = true
    }

    override fun json(json: Json): QueryOptionsBuilder = apply {
        this.json = json
    }

    fun build(): QueryOptions = QueryOptions(
        typeHandlers = typeHandlers,
        compositeAsMapTypes = compositeAsMapTypes,
        customCompositeMappers = customCompositeMappers,
        returnAllCompositesAsMaps = returnAllCompositesAsMaps,
        json = json
    )

    fun copy(): DatabaseQueryOptionsBuilder {
        return DatabaseQueryOptionsBuilder().apply {
            typeHandlers.addAll(typeHandlers)
            compositeAsMapTypes.addAll(compositeAsMapTypes)
            customCompositeMappers.putAll(customCompositeMappers)
            returnAllCompositesAsMaps = this@DatabaseQueryOptionsBuilder.returnAllCompositesAsMaps
            json = this@DatabaseQueryOptionsBuilder.json
        }
    }
}

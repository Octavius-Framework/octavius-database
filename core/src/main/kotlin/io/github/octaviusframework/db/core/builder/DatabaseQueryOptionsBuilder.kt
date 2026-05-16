package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.annotation.PgCompositeMapper
import io.github.octaviusframework.db.api.builder.QueryOptions
import io.github.octaviusframework.db.api.builder.QueryOptionsBuilder
import io.github.octaviusframework.db.api.type.QualifiedName
import io.github.octaviusframework.db.api.type.TypeHandler

internal class DatabaseQueryOptionsBuilder : QueryOptionsBuilder {

    private val typeHandlers = mutableListOf<TypeHandler<*>>()
    private val compositeAsMapTypes = mutableSetOf<QualifiedName>()
    private val customCompositeMappers = mutableMapOf<QualifiedName, PgCompositeMapper<*>>()

    private var returnAllCompositesAsMaps = false

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

    fun build(): QueryOptions = QueryOptions(
        typeHandlers = typeHandlers,
        compositeAsMapTypes = compositeAsMapTypes,
        customCompositeMappers = customCompositeMappers,
        returnAllCompositesAsMaps = returnAllCompositesAsMaps
    )

    fun copy(): DatabaseQueryOptionsBuilder {
        return DatabaseQueryOptionsBuilder().apply {
            typeHandlers.addAll(typeHandlers)
            compositeAsMapTypes.addAll(compositeAsMapTypes)
            customCompositeMappers.putAll(customCompositeMappers)
            returnAllCompositesAsMaps = this@DatabaseQueryOptionsBuilder.returnAllCompositesAsMaps
        }
    }
}

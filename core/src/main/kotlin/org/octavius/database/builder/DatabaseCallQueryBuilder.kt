package org.octavius.database.builder

import org.octavius.data.DataResult
import org.octavius.data.builder.CallQueryBuilder
import org.octavius.data.map
import org.octavius.database.RowMappers
import org.octavius.database.type.KotlinToPostgresConverter
import org.octavius.database.type.registry.PgParamMode
import org.octavius.database.type.registry.TypeRegistry
import org.springframework.jdbc.core.JdbcTemplate

internal class DatabaseCallQueryBuilder(
    jdbcTemplate: JdbcTemplate,
    private val typeRegistry: TypeRegistry,
    kotlinToPostgresConverter: KotlinToPostgresConverter,
    rowMappers: RowMappers,
    private val procedureName: String
) : AbstractQueryBuilder<CallQueryBuilder>(jdbcTemplate, kotlinToPostgresConverter, rowMappers, null), CallQueryBuilder {

    override val canReturnResultsByDefault = true
    private var outTypeOverrides: Map<String, String> = emptyMap()

    override fun outTypes(outTypes: Map<String, String>): CallQueryBuilder {
        this.outTypeOverrides = outTypes
        return this
    }

    override fun buildSql(): String {
        val procDef = typeRegistry.getProcedureDefinition(procedureName)
        val sqlFragments = mutableListOf<String>()

        for (param in procDef.params) {
            when (param.mode) {
                PgParamMode.IN -> {
                    sqlFragments.add(":${param.name}")
                }
                PgParamMode.OUT -> {
                    val resolvedType = outTypeOverrides[param.name] ?: param.typeName
                    sqlFragments.add("NULL::$resolvedType")
                }
                PgParamMode.INOUT -> {
                    sqlFragments.add(":${param.name}")
                }
            }
        }

        return "CALL $procedureName(${sqlFragments.joinToString(", ")})"
    }

    override fun executeCall(params: Map<String, Any?>): DataResult<Map<String, Any?>> {
        val procDef = typeRegistry.getProcedureDefinition(procedureName)
        val hasOutParams = procDef.params.any { it.mode == PgParamMode.OUT || it.mode == PgParamMode.INOUT }

        return if (!hasOutParams) {
            // Use AbstractQueryBuilder.execute (which returns Int)
            execute(params).map { emptyMap() }
        } else {
            // Use AbstractQueryBuilder.toSingleStrict (which returns Map<String, Any?>)
            toSingleStrict(params)
        }
    }

    override fun copy(): DatabaseCallQueryBuilder {
        val newBuilder = DatabaseCallQueryBuilder(
            this.jdbcTemplate,
            this.typeRegistry,
            this.kotlinToPostgresConverter,
            this.rowMappers,
            this.procedureName
        )
        newBuilder.copyBaseStateFrom(this)
        newBuilder.outTypeOverrides = this.outTypeOverrides
        return newBuilder
    }
}

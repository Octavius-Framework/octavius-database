package org.octavius.database.builder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.octavius.data.DataResult
import org.octavius.data.builder.CallQueryBuilder
import org.octavius.data.exception.QueryExecutionException
import org.octavius.database.type.KotlinToPostgresConverter
import org.octavius.database.type.ResultSetValueExtractor
import org.octavius.database.type.registry.PgParamMode
import org.octavius.database.type.registry.PgProcedureDefinition
import org.octavius.database.type.registry.TypeRegistry
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate

internal class DatabaseCallQueryBuilder(
    private val jdbcTemplate: JdbcTemplate,
    private val typeRegistry: TypeRegistry,
    private val kotlinToPostgresConverter: KotlinToPostgresConverter,
    private val resultSetValueExtractor: ResultSetValueExtractor,
    private val procedureName: String
) : CallQueryBuilder {

    override fun execute(params: Map<String, Any?>): DataResult<Map<String, Any?>> {
        val procDef = typeRegistry.getProcedureDefinition(procedureName)
        val plan = buildCallPlan(procDef, params)

        logger.debug { "Executing procedure call: ${plan.sql} with params: $params" }

        return try {
            val outValues = jdbcTemplate.execute(ConnectionCallback { connection ->
                connection.prepareStatement(plan.sql).use { ps ->
                    plan.bindValues.forEachIndexed { index, value ->
                        ps.setObject(index + 1, value)
                    }

                    val hasResultSet = ps.execute()

                    if (plan.outParamNames.isEmpty()) {
                        emptyMap()
                    } else {
                        check(hasResultSet) {
                            "Procedure '$procedureName' has OUT parameters but returned no ResultSet"
                        }
                        ps.resultSet.use { rs ->
                            check(rs.next()) {
                                "Procedure '$procedureName' returned an empty ResultSet"
                            }
                            plan.outParamNames.mapIndexed { index, name ->
                                name to resultSetValueExtractor.extract(rs, index + 1)
                            }.toMap()
                        }
                    }
                }
            })
            DataResult.Success(outValues)
        } catch (e: Exception) {
            val executionException = QueryExecutionException(
                sql = plan.sql,
                params = params,
                cause = e
            )
            logger.error(executionException) { "Procedure call failed" }
            DataResult.Failure(executionException)
        }
    }

    // -------------------------------------------------------------------------
    //  CALL PLAN BUILDING
    // -------------------------------------------------------------------------

    /**
     * @property sql        The CALL statement, e.g. `CALL proc(ROW(?,?)::type, NULL::text)`
     * @property bindValues Flat list of JDBC `?` values in order (only from IN/INOUT params)
     * @property outParamNames Names of OUT/INOUT params, in ResultSet column order
     */
    private data class CallPlan(
        val sql: String,
        val bindValues: List<Any?>,
        val outParamNames: List<String>
    )

    /**
     * Builds the CALL SQL from procedure metadata.
     *
     * - **IN** params are expanded (composites → `ROW(?,…)::type`, lists → `ARRAY[?,…]`)
     *   and their values are collected into [CallPlan.bindValues].
     * - **OUT** params become `NULL::typeName` literals — no JDBC bind, PostgreSQL returns
     *   their values as ResultSet columns.
     * - **INOUT** params are both bound (IN value) and recorded as OUT names.
     */
    private fun buildCallPlan(procDef: PgProcedureDefinition, userParams: Map<String, Any?>): CallPlan {
        val sqlFragments = mutableListOf<String>()
        val bindValues = mutableListOf<Any?>()
        val outParamNames = mutableListOf<String>()

        for (param in procDef.params) {
            when (param.mode) {
                PgParamMode.IN -> {
                    val (fragment, expandedValues) = kotlinToPostgresConverter.expandSingleValue(userParams[param.name])
                    sqlFragments.add(fragment)
                    bindValues.addAll(expandedValues)
                }

                PgParamMode.OUT -> {
                    sqlFragments.add("NULL::${param.typeName}")
                    outParamNames.add(param.name)
                }

                PgParamMode.INOUT -> {
                    val (fragment, expandedValues) = kotlinToPostgresConverter.expandSingleValue(userParams[param.name])
                    sqlFragments.add(fragment)
                    bindValues.addAll(expandedValues)
                    outParamNames.add(param.name)
                }
            }
        }

        val sql = "CALL $procedureName(${sqlFragments.joinToString(", ")})"
        return CallPlan(sql, bindValues, outParamNames)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

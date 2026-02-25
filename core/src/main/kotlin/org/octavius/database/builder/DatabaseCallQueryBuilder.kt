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
                    for ((position, value) in plan.inParams) {
                        ps.setObject(position, value)
                    }
                    // OUT params are not bound — they use literal NULL::type in SQL

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
                                val columnIndex = index + 1
                                name to resultSetValueExtractor.extract(rs, columnIndex)
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

    private data class CallPlan(
        val sql: String,
        val inParams: List<Pair<Int, Any?>>,
        val outParamNames: List<String>
    )

    /**
     * Builds the CALL SQL and tracks JDBC positions for all parameters.
     *
     * Each IN parameter may expand to multiple JDBC `?` placeholders
     * (e.g., a composite with 5 fields becomes `ROW(?, ?, ?, ?, ?)::type_name`,
     * a list becomes `ARRAY[?, ?, ?]`). OUT parameters get a single `?` placeholder
     * bound to NULL — PostgreSQL requires all parameter slots in CALL but returns
     * OUT values as ResultSet columns.
     */
    private fun buildCallPlan(procDef: PgProcedureDefinition, userParams: Map<String, Any?>): CallPlan {
        val sqlFragments = mutableListOf<String>()
        val inParams = mutableListOf<Pair<Int, Any?>>()
        val outParamNames = mutableListOf<String>()
        var nextPosition = 1

        for (param in procDef.params) {
            when (param.mode) {
                PgParamMode.IN -> {
                    val (fragment, expandedValues) = kotlinToPostgresConverter.expandSingleValue(userParams[param.name])
                    sqlFragments.add(fragment)
                    expandedValues.forEachIndexed { i, value ->
                        inParams.add((nextPosition + i) to value)
                    }
                    nextPosition += expandedValues.size
                }

                PgParamMode.OUT -> {
                    sqlFragments.add("NULL::${param.typeName}")
                    outParamNames.add(param.name)
                }

                PgParamMode.INOUT -> {
                    val (fragment, expandedValues) = kotlinToPostgresConverter.expandSingleValue(userParams[param.name])
                    check(expandedValues.size == 1) {
                        "INOUT parameter '${param.name}' expanded to ${expandedValues.size} JDBC parameters. " +
                                "INOUT parameters must be simple types (single JDBC parameter)."
                    }
                    sqlFragments.add(fragment)
                    inParams.add(nextPosition to expandedValues[0])
                    outParamNames.add(param.name)
                    nextPosition++
                }
            }
        }

        val sql = "CALL $procedureName(${sqlFragments.joinToString(", ")})"
        return CallPlan(sql, inParams, outParamNames)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

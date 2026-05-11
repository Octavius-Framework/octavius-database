package io.github.octaviusframework.db.core.exception

import io.github.octaviusframework.db.api.exception.*
import org.postgresql.util.PSQLException
import java.sql.SQLException

/**
 * A specialized translator that converts low-level database exceptions into a structured hierarchy 
 * of Octavius [DatabaseException]s.
 *
 * This component is central to the "Fail-Safe SQL" philosophy of the framework. It categorizes 
 * [SQLException]s and PostgreSQL-specific [PSQLException]s based on their `SQLSTATE` 
 * error codes, providing developers with actionable, high-level information about:
 * - **Integrity Violations:** Unique, Foreign Key, Not Null, and Check constraints.
 * - **Statement Errors:** Syntax errors, permission denied, or missing objects (tables/columns).
 * - **Concurrency Issues:** Deadlocks and lock timeouts.
 * - **Connection Problems:** Pool exhaustion, server disconnection, or resource limits.
 *
 * Each translated exception is enriched with a [QueryContext], which includes the original 
 * SQL statement and its parameters, making debugging and logging significantly more effective.
 */
object ExceptionTranslator {

    /**
     * Translates any [Throwable] into an Octavius [DatabaseException].
     * Prioritizes [SQLException] and its PostgreSQL-specific error codes (SQLSTATE).
     *
     * @param ex The original exception caught during database operation.
     * @param queryContext Metadata about the failed query.
     * @return Translated or original [Exception] for non database Exceptions.
     */
    fun translate(ex: Throwable, queryContext: QueryContext): Throwable {
        when (ex) {
            // BadStatementException from params or created here
            // TypeMappingException is without context in AbstractQueryBuilder
            // TypeRegistryException is without context in AbstractQueryBuilder
            is BadStatementException, is TypeMappingException, is TypeRegistryException -> throw ex.withContext(queryContext)
            // InitializationException -> only on start - impossible here
            // ConstraintViolationException -> created here
            // ConnectionException -> created here
            is DataOperationException -> return ex.withContext(queryContext) // EMPTY_RESULT - rest created here
            // ConcurrencyException -> created here
            // UnknownDatabaseException -> created here
            // TransactionException -> created here
            is StepDependencyException -> throw ex // Context added inside TransactionPlanExecutor
        }

        // If it's a wrapper, find the underlying SQLException
        val sqlException = findSqlException(ex)
        if (sqlException != null) {
            return translateSqlException(sqlException, queryContext)
        }

        // Rest of Exceptions are rethrown
        throw ex
    }

    // Mostly for transactionExceptions from Spring Integration Module
    private fun findSqlException(ex: Throwable): SQLException? {
        var cause: Throwable? = ex
        while (cause != null) {
            if (cause is SQLException) return cause
            cause = cause.cause
        }
        return null
    }

    /**
     * Main translation logic based on PostgreSQL SQLSTATE codes.
     */
    private fun translateSqlException(sqlEx: SQLException, queryContext: QueryContext): OctaviusException {
        val state = sqlEx.sqlState ?: ""
        val pgMetadata = extractPostgresMetadata(sqlEx)

        return when {
            // Class 08 — Connection Exception
            state.startsWith("08") -> ConnectionException(sqlEx.message ?: "Connection error", queryContext, sqlEx)

            // Class 22 — Data Exception (Invalid data provided by the user)
            state.startsWith("22") -> DataOperationException(
                messageEnum =  DataOperationExceptionMessage.INVALID_DATA_FORMAT,
                queryContext = queryContext,
                cause = sqlEx
            )

            // Class 23 — Integrity Constraint Violation
            state.startsWith("23") -> {
                val messageEnum = when (state) {
                    "23502" -> ConstraintViolationExceptionMessage.NOT_NULL_VIOLATION
                    "23503" -> ConstraintViolationExceptionMessage.FOREIGN_KEY_VIOLATION
                    "23505" -> ConstraintViolationExceptionMessage.UNIQUE_CONSTRAINT_VIOLATION
                    "23514" -> ConstraintViolationExceptionMessage.CHECK_CONSTRAINT_VIOLATION
                    else -> ConstraintViolationExceptionMessage.DATA_INTEGRITY
                }
                ConstraintViolationException(
                    messageEnum = messageEnum,
                    tableName = pgMetadata.table,
                    columnName = pgMetadata.column,
                    constraintName = pgMetadata.constraint,
                    queryContext = queryContext,
                    cause = sqlEx
                )
            }

            // Class 25 — Invalid Transaction State
            state.startsWith("25") -> BadStatementException(
                BadStatementExceptionMessage.INVALID_TRANSACTION_STATE,
                queryContext,
                sqlEx
            )

            // Class 40 — Transaction Rollback
            state.startsWith("40") -> {
                val errorType = when (state) {
                    "40P01" -> TransactionExceptionMessage.DEADLOCK
                    "40001" -> TransactionExceptionMessage.SERIALIZATION_FAILURE
                    "40002" -> return ConstraintViolationException(
                        messageEnum = ConstraintViolationExceptionMessage.DEFFERED_CONSTRAINT_VIOLATION,
                        queryContext = queryContext,
                        cause = sqlEx
                    )
                    "40003" -> TransactionExceptionMessage.STATEMENT_COMPLETION_UNKNOWN
                    "40000" -> TransactionExceptionMessage.TRANSACTION_ROLLBACK
                    else -> TransactionExceptionMessage.TRANSACTION_ROLLBACK
                }
                TransactionException(errorType, queryContext, sqlEx)
            }

            // Class 42 — Syntax Error or Access Rule Violation
            state.startsWith("42") -> {
                if (state == "42501") {
                    DataOperationException(
                        messageEnum = DataOperationExceptionMessage.PERMISSION_DENIED,
                        queryContext = queryContext,
                        cause = sqlEx
                    )
                }
                val messageEnum = when (state) {
                    "42601", "42602", "42622", "42939", "42000" -> BadStatementExceptionMessage.SYNTAX_ERROR
                    "42703", "42883", "42P01", "42P02", "42704" -> BadStatementExceptionMessage.UNDEFINED_OBJECT
                    "42701", "42723", "42P03", "42P04", "42P05", "42P06", "42P07", "42712", "42710" -> BadStatementExceptionMessage.DUPLICATE_OBJECT
                    "42702", "42725", "42P08", "42P09" -> BadStatementExceptionMessage.AMBIGUOUS_OBJECT
                    "42804", "42P18", "42846", "42P21", "42P22" -> BadStatementExceptionMessage.DATA_TYPE_ERROR
                    else -> BadStatementExceptionMessage.INVALID_DEFINITION
                }
                BadStatementException(messageEnum, queryContext, sqlEx)
            }

            // Class 57 — Operator Intervention
            state == "57014" -> TransactionException(TransactionExceptionMessage.TIMEOUT, queryContext, sqlEx)
            state.startsWith("57") || state.startsWith("53") || state.startsWith("54") || state.startsWith("55") || state.startsWith("58") || state.startsWith("XX") -> 
                ConnectionException("Database system error: ${sqlEx.message}", queryContext, sqlEx)

            // Class P0 — PL/pgSQL Error
            state.startsWith("P0") -> UnknownDatabaseException("PL/pgSQL Error: ${sqlEx.message}", queryContext, sqlEx)

            else -> UnknownDatabaseException(
                sqlEx.message ?: "Unknown SQL Error (State: $state)",
                queryContext,
                sqlEx
            ).withContext(queryContext)
        }
    }

    private fun extractPostgresMetadata(sqlEx: SQLException?): PostgresErrorMetadata {
        if (sqlEx is PSQLException) {
            val serverError = sqlEx.serverErrorMessage
            if (serverError != null) {
                return PostgresErrorMetadata(
                    table = serverError.table,
                    column = serverError.column,
                    constraint = serverError.constraint,
                    detail = serverError.detail
                )
            }
        }
        return PostgresErrorMetadata()
    }

    private data class PostgresErrorMetadata(
        val table: String? = null,
        val column: String? = null,
        val constraint: String? = null,
        val detail: String? = null
    )
}

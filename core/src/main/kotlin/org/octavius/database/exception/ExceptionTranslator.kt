package org.octavius.database.exception

import org.octavius.data.exception.*
import org.postgresql.util.PSQLException
import org.springframework.dao.*
import org.springframework.jdbc.BadSqlGrammarException
import java.sql.SQLException

object ExceptionTranslator {

    fun translate(ex: Throwable, queryContext: QueryContext): DatabaseException {
        return when (ex) {
            is StepDependencyException -> ex 
            is CodeExecutionException -> ex.withContext(queryContext)
            is DatabaseException -> ex
            is DataAccessException -> translateSpringException(ex, queryContext)
            else -> UnknownDatabaseException(message = ex.message ?: "N/A", ex)
        }
    }

    private fun translateSpringException(ex: DataAccessException, queryContext: QueryContext): DatabaseException {
        val sqlException = findSqlException(ex)
        val pgMetadata = extractPostgresMetadata(sqlException)

        return when (ex) {
            is DuplicateKeyException -> ConstraintViolationException(
                messageEnum = ConstraintViolationExceptionMessage.UNIQUE_CONSTRAINT_VIOLATION,
                tableName = pgMetadata.table,
                constraintName = pgMetadata.constraint,
                queryContext = queryContext,
                cause = sqlException
            )
            is PessimisticLockingFailureException -> ConcurrencyException(
                errorType = ConcurrencyErrorType.DEADLOCK,
                queryContext = queryContext,
                cause = sqlException
            )
            is BadSqlGrammarException -> GrammarException(
                message = sqlException?.message ?: "SQL Grammar error",
                queryContext = queryContext,
                cause = sqlException
            )
            is DataIntegrityViolationException -> {
                val (type, constraint) = parsePostgresError(sqlException)
                ConstraintViolationException(
                    messageEnum = type,
                    tableName = pgMetadata.table,
                    columnName = pgMetadata.column,
                    constraintName = pgMetadata.constraint ?: constraint ?: extractConstraintName(ex),
                    queryContext = queryContext,
                    cause = sqlException
                )
            }
            is QueryTimeoutException, is TransientDataAccessException -> ConcurrencyException(
                errorType = ConcurrencyErrorType.TIMEOUT,
                queryContext = queryContext,
                cause = sqlException
            )
            is DataAccessResourceFailureException -> ConnectionException(
                message = "Database connection failed",
                cause = sqlException
            )
            else -> {
                val state = sqlException?.sqlState
                when {
                    state?.startsWith("42") == true -> GrammarException(sqlException.message ?: "SQL Error", queryContext, sqlException)
                    state == "28000" || state == "42501" -> PermissionException(sqlException.message ?: "Permission denied", queryContext, sqlException)
                    else -> ConstraintViolationException(
                        messageEnum = ConstraintViolationExceptionMessage.UNKNOWN,
                        tableName = pgMetadata.table,
                        queryContext = queryContext,
                        cause = sqlException
                    )
                }
            }
        }
    }

    private fun extractConstraintName(ex: DataAccessException): String? {
        val message = ex.mostSpecificCause.message ?: return null
        val regex = "constraint \"([^\"]+)\"".toRegex()
        return regex.find(message)?.groupValues?.get(1)
    }

    private fun findSqlException(ex: Throwable): SQLException? {
        var cause: Throwable? = ex
        while (cause != null) {
            if (cause is SQLException) return cause
            cause = cause.cause
        }
        return null
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

    private fun parsePostgresError(sqlEx: SQLException?): Pair<ConstraintViolationExceptionMessage, String?> {
        if (sqlEx == null) return ConstraintViolationExceptionMessage.DATA_INTEGRITY to null
        
        return when (sqlEx.sqlState) {
            "23505" -> ConstraintViolationExceptionMessage.UNIQUE_CONSTRAINT_VIOLATION to null
            "23503" -> ConstraintViolationExceptionMessage.FOREIGN_KEY_VIOLATION to null
            "23502" -> ConstraintViolationExceptionMessage.NOT_NULL_VIOLATION to null
            "23514" -> ConstraintViolationExceptionMessage.CHECK_CONSTRAINT_VIOLATION to null
            else -> ConstraintViolationExceptionMessage.DATA_INTEGRITY to null
        }
    }

    private data class PostgresErrorMetadata(
        val table: String? = null,
        val column: String? = null,
        val constraint: String? = null,
        val detail: String? = null
    )
}

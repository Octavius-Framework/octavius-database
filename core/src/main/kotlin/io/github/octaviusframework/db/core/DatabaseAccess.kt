package io.github.octaviusframework.db.core

import io.github.octaviusframework.db.api.DataAccess
import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.QueryOperations
import io.github.octaviusframework.db.api.builder.*
import io.github.octaviusframework.db.api.exception.DatabaseException
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.api.notification.PgChannelListener
import io.github.octaviusframework.db.api.transaction.IsolationLevel
import io.github.octaviusframework.db.api.transaction.TransactionPlan
import io.github.octaviusframework.db.api.transaction.TransactionPlanResult
import io.github.octaviusframework.db.api.transaction.TransactionPropagation
import io.github.octaviusframework.db.core.builder.*
import io.github.octaviusframework.db.core.exception.ExceptionTranslator
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import io.github.octaviusframework.db.core.jdbc.JdbcTransactionProvider
import io.github.octaviusframework.db.core.jdbc.RowMappers
import io.github.octaviusframework.db.core.notification.DatabasePgChannelListener
import io.github.octaviusframework.db.core.transaction.TransactionPlanExecutor
import io.github.octaviusframework.db.core.type.KotlinToPostgresConverter
import io.github.octaviusframework.db.core.type.registry.TypeRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection

internal class DatabaseAccess(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionProvider: JdbcTransactionProvider,
    typeRegistry: TypeRegistry,
    private val kotlinToPostgresConverter: KotlinToPostgresConverter,
    private val listenerConnectionFactory: () -> Connection,
    private val onClose: (() -> Unit)? = null
) : DataAccess {
    private val rowMappers = RowMappers(typeRegistry)
    val transactionPlanExecutor = TransactionPlanExecutor(transactionProvider)
    // --- QueryOperations implementation (for single queries and transaction usage) ---

    override fun select(vararg columns: String): SelectQueryBuilder {
        return DatabaseSelectQueryBuilder(
            jdbcTemplate,
            rowMappers,
            kotlinToPostgresConverter,
            columns.joinToString(",\n")
        )
    }

    override fun update(table: String): UpdateQueryBuilder {
        return DatabaseUpdateQueryBuilder(jdbcTemplate, kotlinToPostgresConverter, rowMappers, table)
    }

    override fun insertInto(table: String): InsertQueryBuilder {
        return DatabaseInsertQueryBuilder(jdbcTemplate, kotlinToPostgresConverter, rowMappers, table)
    }

    override fun deleteFrom(table: String): DeleteQueryBuilder {
        return DatabaseDeleteQueryBuilder(jdbcTemplate, kotlinToPostgresConverter, rowMappers, table)
    }

    override fun rawQuery(sql: String): RawQueryBuilder {
        return DatabaseRawQueryBuilder(jdbcTemplate, kotlinToPostgresConverter, rowMappers, sql)
    }

    //--- Transaction management implementation ---

    override fun executeTransactionPlan(
        plan: TransactionPlan,
        propagation: TransactionPropagation,
        isolation: IsolationLevel,
        readOnly: Boolean,
        timeoutSeconds: Int?
    ): DataResult<TransactionPlanResult> {
        return transactionPlanExecutor.execute(plan, propagation, isolation, readOnly, timeoutSeconds)
    }

    override fun <T> transaction(
        propagation: TransactionPropagation,
        isolation: IsolationLevel,
        readOnly: Boolean,
        timeoutSeconds: Int?,
        block: (tx: QueryOperations) -> DataResult<T>
    ): DataResult<T> {
        return try {
            transactionProvider.execute(propagation, isolation, readOnly, timeoutSeconds) { status ->
                // `this` is an instance of `QueryOperations`, so we pass it directly.
                val result = block(this)

                // If any operation inside the block returned Failure, we roll back the transaction.
                // This allows controlled rollback without throwing an exception!
                if (result is DataResult.Failure) {
                    logger.warn { "Transaction block returned Failure. Rolling back transaction." }
                    status.setRollbackOnly()
                }
                result // Return original result (Success or Failure)
            }
        } catch (e: DatabaseException) {
            // There is no additional context here so there is nothing to do with this exception
            // It should be logged - technically someone is throwing it instead of returning or it is from toDataMap/toDataObject
            // Because it is unreasonable to throw from queries we are assuming that this error has not been logged
            logger.error(e) { "A DatabaseException was thrown inside the transaction block. Rolling back." }
            DataResult.Failure(e)
        } catch (e: Exception) {
            // Catch any other unexpected exception
            // There is no additional context here
            val context = QueryContext("TRANSACTION_OPERATION", emptyMap())
            val ex = ExceptionTranslator.translate(e, context)
            logger.error(e) { "An unexpected exception occurred during transaction management. Rolling back." }
            // Wrap it in standard Failure
            DataResult.Failure(ex)
        }
    }

    override fun notify(channel: String, payload: String?): DataResult<Unit> {
        return rawQuery("SELECT pg_notify(@channel, @payload)").toField<Unit>("channel" to channel, "payload" to payload)
    }

    override fun createChannelListener(): PgChannelListener {
        val connection = listenerConnectionFactory()
        return DatabasePgChannelListener(connection)
    }

    override fun close() {
        onClose?.invoke()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

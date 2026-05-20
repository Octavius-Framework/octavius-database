package io.github.octaviusframework.db.core

import io.github.octaviusframework.db.api.DataAccess
import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.QueryOperations
import io.github.octaviusframework.db.api.annotation.DynamicallyMappable
import io.github.octaviusframework.db.api.builder.*
import io.github.octaviusframework.db.api.exception.DatabaseException
import io.github.octaviusframework.db.api.exception.FatalDatabaseException
import io.github.octaviusframework.db.api.exception.OctaviusException
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.api.exception.TypeMappingException
import io.github.octaviusframework.db.api.exception.TypeMappingExceptionMessage
import io.github.octaviusframework.db.api.notification.PgChannelListener
import io.github.octaviusframework.db.api.transaction.*
import io.github.octaviusframework.db.api.type.DynamicDto
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.sql.Connection

internal class DatabaseAccess(
    jdbcTemplate: JdbcTemplate,
    private val transactionProvider: JdbcTransactionProvider,
    private val typeRegistry: TypeRegistry,
    kotlinToPostgresConverter: KotlinToPostgresConverter,
    private val listenerConnectionFactory: () -> Connection,
    override val json: Json,
    private val onClose: (() -> Unit)? = null
) : DataAccess {
    private val rowMappers = RowMappers(typeRegistry, json)
    private val queryExecutor = QueryExecutor(jdbcTemplate, kotlinToPostgresConverter)
    private val transactionPlanExecutor = TransactionPlanExecutor(transactionProvider)

    // --- QueryOperations implementation (for single queries and transaction usage) ---

    override fun select(vararg columns: String): SelectQueryBuilder {
        return DatabaseSelectQueryBuilder(
            queryExecutor,
            rowMappers,
            typeRegistry,
            columns.joinToString(",\n")
        )
    }

    override fun update(table: String): UpdateQueryBuilder {
        return DatabaseUpdateQueryBuilder(queryExecutor, rowMappers, typeRegistry, table)
    }

    override fun insertInto(table: String): InsertQueryBuilder {
        return DatabaseInsertQueryBuilder(queryExecutor, rowMappers, typeRegistry, table)
    }

    override fun deleteFrom(table: String): DeleteQueryBuilder {
        return DatabaseDeleteQueryBuilder(queryExecutor, rowMappers, typeRegistry, table)
    }

    override fun rawQuery(sql: String): RawQueryBuilder {
        return DatabaseRawQueryBuilder(queryExecutor, rowMappers, typeRegistry, sql)
    }

    override fun toDynamicDto(value: Any): DynamicDto {
        val kClass = value::class
        val typeName = typeRegistry.getDynamicTypeNameForClass(kClass)
            ?: throw TypeMappingException(
                TypeMappingExceptionMessage.JSON_SERIALIZATION_FAILED,
                value = kClass.simpleName,
                targetType = DynamicallyMappable::class.simpleName
            )
        val serializer = typeRegistry.getDynamicSerializer(typeName)
        return DynamicDto.from(value, typeName, serializer, json)
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
        block: QueryOperations.() -> DataResult<T>
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
        } catch (e: OctaviusException) {
            // There is no additional context here so there is nothing to do with this exception
            when (e) {
                // Technically someone is throwing it instead of returning it
                // It is rather unreasonable, but it should be consistent with queries
                is DatabaseException -> return DataResult.Failure(e)
                //
                is FatalDatabaseException -> throw e
            }
        } catch (e: Exception) {
            // Catch any other unexpected exception
            // These errors haven't been logged
            logger.error(e) { "An unexpected exception occurred during transaction management. Rolling back." }
            // There is no additional context here
            val context = QueryContext("TRANSACTION_OPERATION", emptyMap())
            // Try to translate SQLException to DatabaseException
            val ex = ExceptionTranslator.translate(e, context)
            // Wrap it in standard Failure
            DataResult.Failure(ex)
        }
    }

    // Notifications
    override fun notify(channel: String, payload: String?): DataResult<Unit> {
        return rawQuery("SELECT pg_notify(@channel, @payload)").toField<Unit>(
            "channel" to channel,
            "payload" to payload
        )
    }

    override fun notifyStep(channel: String, payload: String?): TransactionStep<Unit> {
        return rawQuery("SELECT pg_notify(@channel, @payload)").asStep()
            .toField<Unit>("channel" to channel, "payload" to payload)
    }

    override fun createChannelListener(): PgChannelListener {
        val connection = listenerConnectionFactory()
        return DatabasePgChannelListener(connection)
    }

    // Other
    override fun close() {
        onClose?.invoke()
    }

    override val enumSerializers: SerializersModule get() = typeRegistry.enumSerializers

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

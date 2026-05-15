package io.github.octaviusframework.db.core.notification

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.api.notification.PgChannelListener
import io.github.octaviusframework.db.api.notification.PgNotification
import io.github.octaviusframework.db.api.type.QualifiedName
import io.github.octaviusframework.db.core.exception.ExceptionTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.postgresql.PGConnection
import java.sql.Connection

internal class DatabasePgChannelListener(
    private val connection: Connection
) : PgChannelListener {
    // All notifications aren't part of transactions
    private val pgConnection: PGConnection = connection.unwrap(PGConnection::class.java)

    override fun listen(vararg channels: String): DataResult<Unit> {
        val sql = channels.joinToString("; ") { "LISTEN ${QualifiedName.quoteIdentifier(it)}" }
        return try {
            connection.createStatement().use { stmt ->
                stmt.execute(sql)
            }
            DataResult.Success(Unit)
        } catch (e: Exception) {
            // Translate all exceptions - it will be SQLException
            val translated = ExceptionTranslator.translate(e, QueryContext(sql, emptyMap()))
            logger.error(translated) { "Error executing LISTEN on channels: ${channels.toList()}" }
            DataResult.Failure(translated)
        }
    }

    override fun unlisten(vararg channels: String): DataResult<Unit> {
        val sql = channels.joinToString("; ") { "UNLISTEN ${QualifiedName.quoteIdentifier(it)}" }
        return try {
            connection.createStatement().use { stmt ->
                stmt.execute(sql)
            }
            DataResult.Success(Unit)
        } catch (e: Exception) {
            // Translate all exceptions - it will be SQLException
            val translated = ExceptionTranslator.translate(e, QueryContext(sql, emptyMap()))
            logger.error(translated) { "Error executing UNLISTEN on channels: ${channels.toList()}" }
            DataResult.Failure(translated)
        }
    }

    override fun unlistenAll(): DataResult<Unit> {
        val sql = "UNLISTEN *"
        return try {
            connection.createStatement().use { stmt ->
                stmt.execute(sql)
            }
            DataResult.Success(Unit)
        } catch (e: Exception) {
            // Translate all exceptions - it will be SQLException
            val translated = ExceptionTranslator.translate(e, QueryContext(sql, emptyMap()))
            logger.error(translated) { "Error executing UNLISTEN *" }
            DataResult.Failure(translated)
        }
    }

    override fun notifications(ioDispatcher: CoroutineDispatcher): Flow<PgNotification> = flow {
        while (currentCoroutineContext().isActive) {
            val notifs = pgConnection.getNotifications(POLL_TIMEOUT_MS)
            notifs?.forEach { notif ->
                emit(PgNotification(notif.name, notif.parameter, notif.pid))
            }
        }
    }.flowOn(ioDispatcher) // Notifications aren't part of transactions

    override fun close() {
        if (connection.isClosed) return
        try {
            unlistenAll()
        } catch (e: Exception) {
            logger.warn(e) { "Error executing UNLISTEN * during listener close" }
        }
        try {
            connection.close()
        } catch (e: Exception) {
            logger.warn(e) { "Error closing listener connection" }
        }
    }

    companion object {
        private const val POLL_TIMEOUT_MS = 500
        private val logger = KotlinLogging.logger {}
    }
}

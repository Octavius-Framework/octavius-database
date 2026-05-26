package io.github.octaviusframework.db.core.jdbc

import io.github.octaviusframework.db.api.transaction.IsolationLevel
import io.github.octaviusframework.db.api.transaction.TransactionPropagation
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * Interface for managing database connections and transactions.
 * Decouples the core logic from specific transaction management implementations (like Spring).
 * 
 * Each [DataAccess][io.github.octaviusframework.db.api.DataAccess] instance is backed by exactly one [JdbcTransactionProvider].
 */
interface JdbcTransactionProvider {
    /**
     * The underlying data source used for obtaining connections.
     */
    val dataSource: DataSource

    /**
     * Obtains a connection for the current execution context.
     * If a transaction is active, returns the connection bound to that transaction.
     * Otherwise, returns a new connection from the [dataSource].
     */
    fun getConnection(): Connection

    /**
     * Releases the given [connection].
     * If the connection is part of an active transaction, it is NOT closed.
     * Otherwise, it is returned to the pool (closed).
     */
    fun releaseConnection(connection: Connection)

    /**
     * Executes a [block] of code within a transaction context.
     * 
     * @param propagation The transaction propagation behavior (e.g., REQUIRED, REQUIRES_NEW).
     * @param isolation The isolation level to use. [IsolationLevel.DEFAULT] leaves the current state unchanged.
     * @param readOnly Hint to the database for read-only optimization.
     * @param statementTimeout Optional timeout for single statement. Working on in Core.
     * @param transactionTimeout Optional timeout for the entire transaction (requires PG 17+ in Core).
     * @param block The logic to execute within the transaction.
     */
    fun <T> execute(
        propagation: TransactionPropagation,
        isolation: IsolationLevel = IsolationLevel.DEFAULT,
        readOnly: Boolean = false,
        statementTimeout: Duration? = null,
        transactionTimeout: Duration? = null,
        block: (TransactionStatus) -> T
    ): T

    /**
     * Applies the current transaction timeout to the given [statement].
     * 
     * This is a critical bridge for Spring integration, as Spring tracks timeouts via ThreadLocal
     * and requires manual propagation to JDBC Statements.
     */
    fun applyTimeout(statement: Statement)
}

/**
 * Minimal interface to control the current transaction.
 */
fun interface TransactionStatus {
    fun setRollbackOnly()
}

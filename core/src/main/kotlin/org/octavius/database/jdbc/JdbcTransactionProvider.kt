package org.octavius.database.jdbc

import org.octavius.data.transaction.IsolationLevel
import org.octavius.data.transaction.TransactionPropagation
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

/**
 * Interface for managing database connections and transactions.
 * Decouples the core logic from specific transaction management implementations (like Spring).
 */
interface JdbcTransactionProvider {
    val dataSource: DataSource

    fun getConnection(): Connection
    fun releaseConnection(connection: Connection)

    fun <T> execute(
        propagation: TransactionPropagation,
        isolation: IsolationLevel = IsolationLevel.DEFAULT,
        readOnly: Boolean = false,
        timeoutSeconds: Int? = null,
        block: (TransactionStatus) -> T
    ): T

    // Spring...
    fun applyTimeout(statement: Statement)
}

/**
 * Minimal interface to control the current transaction.
 */
interface TransactionStatus {
    fun setRollbackOnly()
}

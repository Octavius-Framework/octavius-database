package org.octavius.database.jdbc

import org.octavius.data.transaction.TransactionPropagation
import java.sql.Connection
import java.sql.Savepoint
import javax.sql.DataSource

/**
 * Default implementation of [JdbcTransactionProvider] using [ThreadLocal] to manage transactions.
 * This implementation does not depend on any external transaction managers (like Spring).
 */
internal class DefaultJdbcTransactionProvider(override val dataSource: DataSource) : JdbcTransactionProvider {

    private val transactionStack = ThreadLocal<MutableList<TransactionContext>>()

    private fun getStack(): MutableList<TransactionContext> {
        var stack = transactionStack.get()
        if (stack == null) {
            stack = mutableListOf()
            transactionStack.set(stack)
        }
        return stack
    }

    override fun getConnection(): Connection {
        val stack = getStack()
        return if (stack.isNotEmpty()) {
            stack.last().connection
        } else {
            dataSource.connection
        }
    }

    override fun releaseConnection(connection: Connection) {
        val stack = getStack()
        // If the connection is the one managed by the current transaction, do not close it.
        // JdbcTemplate will call this after every operation.
        if (stack.isEmpty() || stack.last().connection != connection) {
            connection.close()
        }
    }

    override fun <T> execute(propagation: TransactionPropagation, block: (TransactionStatus) -> T): T {
        val stack = getStack()
        val currentContext = stack.lastOrNull()

        return when (propagation) {
            TransactionPropagation.REQUIRED -> {
                if (currentContext != null) {
                    executeExisting(currentContext, block)
                } else {
                    executeNew(stack, block)
                }
            }
            TransactionPropagation.REQUIRES_NEW -> {
                executeNew(stack, block)
            }
            TransactionPropagation.NESTED -> {
                if (currentContext != null) {
                    executeNested(currentContext, block)
                } else {
                    executeNew(stack, block)
                }
            }
        }
    }

    private fun <T> executeExisting(context: TransactionContext, block: (TransactionStatus) -> T): T {
        context.depth++
        val status = DefaultTransactionStatus { context.rollbackOnly = true }
        return try {
            block(status)
        } catch (e: Throwable) {
            context.rollbackOnly = true
            throw e
        } finally {
            context.depth--
        }
    }

    private fun <T> executeNew(stack: MutableList<TransactionContext>, block: (TransactionStatus) -> T): T {
        val connection = dataSource.connection
        val context = try {
            connection.autoCommit = false
            TransactionContext(connection)
        } catch (e: Throwable) {
            runCatching { connection.close() }
            throw e
        }

        stack.add(context)

        val status = DefaultTransactionStatus { context.rollbackOnly = true }
        return try {
            val result = block(status)

            if (context.rollbackOnly) {
                runCatching { connection.rollback() }
            } else {
                connection.commit()
            }

            result
        } catch (e: Throwable) {
            runCatching { connection.rollback() }
            throw e
        } finally {
            stack.removeLast()
            runCatching { connection.autoCommit = true }
            runCatching { connection.close() }

            if (stack.isEmpty()) {
                transactionStack.remove()
            }
        }
    }

    private fun <T> executeNested(context: TransactionContext, block: (TransactionStatus) -> T): T {
        val savepoint = context.connection.setSavepoint()
        var nestedRollbackOnly = false
        val status = DefaultTransactionStatus { nestedRollbackOnly = true }
        
        return try {
            val result = block(status)
            if (nestedRollbackOnly) {
                context.connection.rollback(savepoint)
            } else {
                runCatching { context.connection.releaseSavepoint(savepoint) }
            }
            result
        } catch (e: Throwable) {
            runCatching { context.connection.rollback(savepoint) }
            throw e
        }
    }

    private class TransactionContext(
        val connection: Connection
    ) {
        var rollbackOnly: Boolean = false
        var depth: Int = 0
    }

    private class DefaultTransactionStatus(private val onRollbackOnly: () -> Unit) : TransactionStatus {
        override fun setRollbackOnly() {
            onRollbackOnly()
        }
    }
}

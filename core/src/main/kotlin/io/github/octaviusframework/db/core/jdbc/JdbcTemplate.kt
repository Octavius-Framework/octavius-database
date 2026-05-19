package io.github.octaviusframework.db.core.jdbc

import io.github.octaviusframework.db.api.exception.BadStatementException
import io.github.octaviusframework.db.api.exception.BadStatementExceptionMessage
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.core.type.PositionalQuery
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource

internal class JdbcTemplate(private val transactionProvider: JdbcTransactionProvider) {

    val dataSource: DataSource get() = transactionProvider.dataSource

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private fun Connection.createWrappedStatement(): Statement {
        return this.createStatement().also {
            transactionProvider.applyTimeout(it)
        }
    }

    private fun Connection.prepareWrappedStatement(sql: String): PreparedStatement {
        return this.prepareStatement(sql).also {
            transactionProvider.applyTimeout(it)
        }
    }

    fun execute(sql: String) {
        val conn = transactionProvider.getConnection()
        try {
            conn.createWrappedStatement().use { stmt ->
                @Suppress("SqlSourceToSinkFlow")
                stmt.execute(sql)
            }
        } finally {
            transactionProvider.releaseConnection(conn)
        }
    }

    fun <T> query(query: PositionalQuery, rowMapper: RowMapper<T>): List<T> {
        val conn = transactionProvider.getConnection()
        try {
            return conn.prepareWrappedStatement(query.sql).use { ps ->
                setParameters(ps, query.params)
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    while (rs.next()) {
                        results.add(rowMapper.mapRow(rs))
                    }
                    results
                }
            }
        } finally {
            transactionProvider.releaseConnection(conn)
        }
    }

    fun update(query: PositionalQuery): Int {
        val conn = transactionProvider.getConnection()
        try {
            return conn.prepareWrappedStatement(query.sql).use { ps ->
                setParameters(ps, query.params)
                ps.executeUpdate()
            }
        } finally {
            transactionProvider.releaseConnection(conn)
        }
    }

    fun query(query: PositionalQuery, fetchSize: Int, resultSetHandler: (ResultSet) -> Unit) {
        val conn = transactionProvider.getConnection()
        try {
            conn.prepareWrappedStatement(query.sql).use { ps ->
                setParameters(ps, query.params)

                if (conn.autoCommit && fetchSize > 0) {
                    throw BadStatementException(
                        messageEnum = BadStatementExceptionMessage.STREAMING_REQUIRES_TRANSACTION,
                        queryContext = QueryContext(query.sql, emptyMap(), query.sql, query.params),
                        cause = IllegalStateException("PostgreSQL driver ignores fetchSize when autoCommit is true.")
                    )
                }
                ps.fetchSize = fetchSize

                ps.executeQuery().use { rs ->
                    resultSetHandler(rs)
                }
            }
        } finally {
            transactionProvider.releaseConnection(conn)
        }
    }
    
    private fun setParameters(ps: PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { index, value ->
            ps.setObject(index + 1, value)
        }
    }
}
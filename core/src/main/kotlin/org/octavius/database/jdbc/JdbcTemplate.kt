package org.octavius.database.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import org.octavius.database.type.PositionalQuery
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
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
                    var rowNum = 0
                    while (rs.next()) {
                        results.add(rowMapper.mapRow(rs, rowNum++))
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

                if (conn.autoCommit) {
                    logger.warn {
                        "POTENTIAL PERFORMANCE ISSUE: Streaming query executed with autoCommit=true. " +
                                "PostgreSQL driver will ignore fetchSize=$fetchSize and load all rows into RAM. " +
                                "Wrap this call in DataAccess.transaction { ... }."
                    }
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
package org.octavius.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.octavius.data.DataAccess
import org.octavius.data.getOrThrow
import org.octavius.database.config.DatabaseConfig
import org.octavius.database.jdbc.DefaultJdbcTransactionProvider
import org.octavius.database.jdbc.JdbcTemplate
import org.octavius.database.type.PositionalQuery
import java.sql.DriverManager


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    protected lateinit var dataAccess: DataAccess

    protected lateinit var dataSource: HikariDataSource

    open val packagesToScan: List<String> = emptyList()

    protected open val scriptName: String? = null
    protected open val sqlToExecuteOnSetup: String? = null

    protected fun loadSql(name: String): String {
        val resource = Thread.currentThread().contextClassLoader.getResource(name)
            ?: throw IllegalArgumentException("Resource $name not found")
        return resource.readText()
    }

    @BeforeAll
    fun setup() {
        val databaseConfig = DatabaseConfig.loadFromFile("test-database.properties")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = databaseConfig.dbUrl
            username = databaseConfig.dbUsername
            password = databaseConfig.dbPassword
        }
        dataSource = HikariDataSource(hikariConfig)
        val jdbcTemplate = JdbcTemplate(DefaultJdbcTransactionProvider(dataSource))
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;")
        scriptName?.let {  jdbcTemplate.execute(loadSql(it)) }
        sqlToExecuteOnSetup?.let { jdbcTemplate.execute(it) }

        dataAccess = OctaviusDatabase.fromDataSource(
            dataSource = dataSource,
            packagesToScan = emptyList(),
            dbSchemas = listOf("public")
        )
    }

    @AfterAll
    fun tearDown() {
        dataSource.close()
    }

}

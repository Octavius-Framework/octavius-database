package io.github.octaviusframework.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.db.api.DataAccess
import io.github.octaviusframework.db.core.CoreTypeInitializer
import io.github.octaviusframework.db.core.config.DatabaseConfig
import io.github.octaviusframework.db.core.config.DynamicDtoSerializationStrategy
import io.github.octaviusframework.db.core.jdbc.DefaultJdbcTransactionProvider
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    protected lateinit var dataAccess: DataAccess

    protected lateinit var dataSource: HikariDataSource

    open val packagesToScan: List<String> = emptyList()

    open val dynamicDtoStrategy: DynamicDtoSerializationStrategy = DynamicDtoSerializationStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS

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

        val connectionUrl = databaseConfig.dbUrl
        val dbName = connectionUrl.substringAfterLast("/")
        if (!connectionUrl.contains("localhost:5432") || dbName != "octavius_test") {
            throw IllegalStateException("ABORTING TEST! Attempting to run on a non-test database. URL: '$connectionUrl'")
        }
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = databaseConfig.dbUrl
            username = databaseConfig.dbUsername
            password = databaseConfig.dbPassword
        }
        dataSource = HikariDataSource(hikariConfig)
        val jdbcTemplate = JdbcTemplate(DefaultJdbcTransactionProvider(dataSource))
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;")

        CoreTypeInitializer.ensureRequiredTypes(jdbcTemplate)
        
        scriptName?.let {  jdbcTemplate.execute(loadSql(it)) }
        sqlToExecuteOnSetup?.let { jdbcTemplate.execute(it) }

        dataAccess = OctaviusDatabase.fromDataSource(
            dataSource = dataSource,
            packagesToScan = packagesToScan,
            dbSchemas = listOf("public"),
            dynamicDtoStrategy = dynamicDtoStrategy
        )
    }

    @AfterAll
    fun tearDown() {
        dataSource.close()
    }

}
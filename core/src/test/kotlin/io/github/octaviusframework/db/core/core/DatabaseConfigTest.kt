package io.github.octaviusframework.db.core.core

import io.github.octaviusframework.db.core.config.DatabaseConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class DatabaseConfigTest {

    @Test
    fun `should correctly map properties to DatabaseConfig`() {
        val props = Properties().apply {
            setProperty("db.url", "jdbc:postgresql://localhost:5432/test")
            setProperty("db.username", "user")
            setProperty("db.password", "pass")
            setProperty("db.schemas", "public, private")
            setProperty("db.packagesToScan", "io.github.octaviusframework.db.api")
            setProperty("db.hikari.maximumPoolSize", "20")
            setProperty("db.hikari.leakDetectionThreshold", "3000")
        }

        val config = DatabaseConfig.fromProperties(props)

        Assertions.assertEquals("jdbc:postgresql://localhost:5432/test", config.dbUrl)
        Assertions.assertEquals("user", config.dbUsername)
        Assertions.assertEquals("pass", config.dbPassword)
        Assertions.assertEquals(listOf("public", "private"), config.dbSchemas)
        Assertions.assertEquals(listOf("io.github.octaviusframework.db.api"), config.packagesToScan)

        // Hikari properties
        Assertions.assertEquals(2, config.hikariProperties.size)
        Assertions.assertEquals("20", config.hikariProperties["maximumPoolSize"])
        Assertions.assertEquals("3000", config.hikariProperties["leakDetectionThreshold"])
        Assertions.assertEquals(true, config.showBanner) // default
    }

    @Test
    fun `should correctly map showBanner property`() {
        val props = Properties().apply {
            setProperty("db.url", "jdbc:postgresql://localhost:5432/test")
            setProperty("db.username", "user")
            setProperty("db.password", "pass")
            setProperty("db.schemas", "public")
            setProperty("db.packagesToScan", "io.github.octaviusframework.db.api")
            setProperty("db.showBanner", "false")
        }

        val config = DatabaseConfig.fromProperties(props)
        Assertions.assertEquals(false, config.showBanner)
    }
}
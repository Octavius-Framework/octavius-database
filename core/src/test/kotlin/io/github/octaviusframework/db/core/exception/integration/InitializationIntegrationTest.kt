package io.github.octaviusframework.db.core.exception.integration

import io.github.octaviusframework.db.api.exception.InitializationException
import io.github.octaviusframework.db.core.OctaviusDatabase
import io.github.octaviusframework.db.core.config.DatabaseConfig
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InitializationIntegrationTest {

    private lateinit var config: DatabaseConfig

    @BeforeAll
    fun setup() {
        config = DatabaseConfig.loadFromFile("test-database.properties")
    }

    @Test
    fun `should throw InitializationException when port is wrong`() {
        // GIVEN: config with wrong port
        val wrongConfig = config.copy(dbUrl = "jdbc:postgresql://localhost:5433/non_existent_db")
        
        // WHEN & THEN
        assertThrows<InitializationException> {
            OctaviusDatabase.fromConfig(wrongConfig)
        }
    }
}

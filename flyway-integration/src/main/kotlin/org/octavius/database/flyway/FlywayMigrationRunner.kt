package org.octavius.database.flyway

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.octavius.data.exception.InitializationException
import org.octavius.data.exception.InitializationExceptionMessage
import javax.sql.DataSource

object FlywayMigrationRunner {
    private val logger = KotlinLogging.logger {}

    /**
     * Creates a migration runner function that can be passed to [OctaviusDatabase.fromDataSource][org.octavius.database.OctaviusDatabase.fromDataSource].
     *
     * @param schemas List of database schemas to manage via Flyway.
     * @param baselineVersion Version to use as the baseline for Flyway. If null, baselining is disabled.
     * @param locations Path to migration files (defaults to "classpath:db/migration").
     */
    fun create(
        schemas: List<String>,
        baselineVersion: String? = null,
        locations: String = "classpath:db/migration"
    ): (DataSource) -> Unit = { dataSource ->

        logger.info { "Checking database migrations via Flyway..." }

        val flywayConfig = Flyway.configure()
            .dataSource(dataSource)
            .schemas(*schemas.toTypedArray())
            .locations(locations)
            .createSchemas(true)

        if (baselineVersion != null) {
            flywayConfig
                .baselineOnMigrate(true)
                .baselineVersion(baselineVersion)
        }

        val flyway = flywayConfig.load()

        try {
            val result = flyway.migrate()
            if (result.migrationsExecuted > 0) {
                logger.info { "Successfully applied ${result.migrationsExecuted} migrations." }
            } else {
                logger.debug { "Database is up to date." }
            }
        } catch (e: Exception) {
            logger.error(e) { "Migration failed!" }
            throw InitializationException(
                InitializationExceptionMessage.MIGRATION_FAILED,
                details = e.message,
                cause = e
            )
        }
    }
}
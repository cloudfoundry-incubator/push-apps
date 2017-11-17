package io.pivotal.pushapps

import org.flywaydb.core.Flyway
import javax.sql.DataSource

class FlywayWrapper(
    private val flyway: Flyway
) {
    fun migrate(dataSource: DataSource, migrationsLocation: String) {
        flyway.dataSource = dataSource
        flyway.setLocations(migrationsLocation)

        flyway.migrate()
    }
}

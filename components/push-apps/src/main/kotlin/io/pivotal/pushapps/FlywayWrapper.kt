package io.pivotal.pushapps

import org.apache.commons.io.FilenameUtils
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import java.nio.file.Files
import java.nio.file.Paths
import javax.sql.DataSource

class FlywayWrapper(
    private val flyway: Flyway
) {
    fun migrate(dataSource: DataSource, migrationsLocation: String, repair: Boolean) {
        if (Files.notExists(Paths.get(migrationsLocation)))
            throw FlywayException("Unable to find migrations folder $migrationsLocation")
        if (pathContainsNoMigrations(migrationsLocation))
            throw FlywayException("Did not find any migrations in $migrationsLocation")

        flyway.dataSource = dataSource
        flyway.setLocations("filesystem:$migrationsLocation")

        if (repair) flyway.repair()

        flyway.migrate()
        flyway.validate()
    }

    private fun pathContainsNoMigrations(migrationsLocation: String): Boolean {
        return Files.list(Paths.get(migrationsLocation)).noneMatch { path ->
            FilenameUtils.getExtension(path.toString()).equals("sql")
        }
    }
}

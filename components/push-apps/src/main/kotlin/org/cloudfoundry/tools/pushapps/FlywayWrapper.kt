package org.cloudfoundry.tools.pushapps

import org.apache.commons.io.FilenameUtils
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Paths
import javax.sql.DataSource

class FlywayWrapper(
    private val createFlywayInstance: () -> Flyway
) {
    fun migrate(dataSource: DataSource, migrationsLocation: String, repair: Boolean, placeholders: Map<String, String>): Mono<Void> {
        return Mono.fromRunnable {
            // Need a new flyway instance for each migration, otherwise it can use the wrong datasource,
            // since this is executed async
            val flyway = createFlywayInstance()

            if (Files.notExists(Paths.get(migrationsLocation)))
                throw FlywayException("Unable to find migrations folder $migrationsLocation")
            if (pathContainsNoMigrations(migrationsLocation))
                throw FlywayException("Did not find any migrations in $migrationsLocation")

            flyway.dataSource = dataSource
            flyway.setLocations("filesystem:$migrationsLocation")
            flyway.placeholders = placeholders

            if (repair) flyway.repair()
            flyway.migrate()
            flyway.validate()
        }
    }

    private fun pathContainsNoMigrations(migrationsLocation: String): Boolean {
        return Files.list(Paths.get(migrationsLocation)).noneMatch { path ->
            FilenameUtils.getExtension(path.toString()).equals("sql")
        }
    }
}

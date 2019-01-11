package unit

import org.apache.commons.io.FilenameUtils
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.tools.pushapps.config.ConfigReader
import org.cloudfoundry.tools.pushapps.config.DatabaseDriver
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.nio.file.Paths

class ConfigReaderTest : Spek({
    fun findConfigPath(fileName: String): String {
        val compiledFileLocation =
            ConfigReaderTest::class.java.getResource("${ConfigReaderTest::class.simpleName}.class")
        val projectPath =
            Paths.get(compiledFileLocation.path.toString(), "../../../../../../src/test/kotlin/support/$fileName")
                .toAbsolutePath().toString()
        return FilenameUtils.normalize(projectPath)
    }

    describe("#parseConfig") {
        context("parsing a minimal config") {
            it("correctly defaults values that are not provided for the pushAppsConfig config") {
                val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("minimalConfig.yml")).get()
                assertThat(pushAppsConfig.pushApps.operationRetryCount).isEqualTo(3)
                assertThat(pushAppsConfig.pushApps.maxInFlight).isEqualTo(2)
                assertThat(pushAppsConfig.pushApps.failedDeploymentLogLinesToShow).isEqualTo(50)
                assertThat(pushAppsConfig.pushApps.migrationTimeoutInMinutes).isEqualTo(15L)
                assertThat(pushAppsConfig.pushApps.cfOperationTimeoutInMinutes).isEqualTo(5L)
            }

            it("correctly defaults values that are not provided for the cf config") {
                val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("minimalConfig.yml")).get()

                assertThat(pushAppsConfig.cf.skipSslValidation).isFalse()
                assertThat(pushAppsConfig.cf.dialTimeoutInMillis).isNull()
            }

            it("correctly defaults values that are not provided for the apps config") {
                val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("minimalConfig.yml")).get()

                val apps = pushAppsConfig.apps
                assertThat(apps).hasSize(1)

                val app1 = apps[0]

                assertThat(app1.name).isEqualTo("some-name")
                assertThat(app1.path).isEqualTo("some-path")
                assertThat(app1.serviceNames).isEmpty()

                //this is a partial list, didn't see the need to be exhaustive
                assertThat(app1.buildpack).isNull()
                assertThat(app1.command).isNull()
                assertThat(app1.environment).isNull()
                assertThat(app1.route).isNull()
            }

            it("correctly defaults values that are not provided for the services config") {
                val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("minimalConfig.yml")).get()

                val services = pushAppsConfig.services
                assertThat(services).hasSize(1)

                val service = services[0]
                assertThat(service.optional).isFalse()
            }

            it("correctly defaults values that are not provided for the db migration config") {
                val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("minimalConfig.yml")).get()

                val migrations = pushAppsConfig.migrations
                assertThat(migrations).hasSize(1)

                val migration = migrations[0]
                assertThat(migration.repair).isFalse()
            }
        }

        it("Parses the pushAppsConfig config") {
            val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()
            assertThat(pushAppsConfig.pushApps.operationRetryCount).isEqualTo(3)
            assertThat(pushAppsConfig.pushApps.maxInFlight).isEqualTo(42)
            assertThat(pushAppsConfig.pushApps.failedDeploymentLogLinesToShow).isEqualTo(12)
            assertThat(pushAppsConfig.pushApps.migrationTimeoutInMinutes).isEqualTo(7L)
            assertThat(pushAppsConfig.pushApps.cfOperationTimeoutInMinutes).isEqualTo(3L)
        }

        it("Parses the cf config") {
            val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()

            assertThat(pushAppsConfig.cf.apiHost).isEqualTo("api.example.com")
            assertThat(pushAppsConfig.cf.username).isEqualTo("some-username")
            assertThat(pushAppsConfig.cf.password).isEqualTo("some-password")
            assertThat(pushAppsConfig.cf.organization).isEqualTo("some-organization")
            assertThat(pushAppsConfig.cf.space).isEqualTo("some-space")
            assertThat(pushAppsConfig.cf.skipSslValidation).isTrue()
            assertThat(pushAppsConfig.cf.dialTimeoutInMillis).isEqualTo(1000)
        }

        context("Parsing the apps config") {
            it("correctly assigns values") {
                val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()

                val apps = pushAppsConfig.apps
                assertThat(apps).hasSize(2)

                val app1 = apps[0]
                assertThat(app1.name).isEqualTo("some-name")
                assertThat(app1.path).isEqualTo("some-path")
                assertThat(app1.buildpack).isEqualTo("some-buildpack")
                assertThat(app1.memory).isEqualTo(456)
                assertThat(app1.diskQuota).isEqualTo(500)
                assertThat(app1.stackPriority).isEqualTo(listOf("some-stack", "some-other-stack"))

                assertThat(app1.environment).isEqualTo(mapOf("FRUIT" to "lemons", "MISSING" to ""))
                assertThat(app1.serviceNames).isEqualTo(listOf("some-service-name"))

                assertThat(app1.route!!.hostname).isEqualTo("lemons")
                assertThat(app1.route!!.path).isEqualTo("/citrus")

                val app2 = apps[1]
                assertThat(app2.memory).isEqualTo(1024)
                assertThat(app2.diskQuota).isEqualTo(2048)
                assertThat(app2.stackPriority).isEqualTo(emptyList<String>())
            }

            context("if blueGreenDeploy is true") {
                it("throws an error unless a route is provided or noRoute is true") {
                    val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("blueGreenDeployConfig.yml"))
                    assertThat(pushAppsConfig).isEmpty()
                }
            }
        }

        it("Parses the services config") {
            val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()

            val services = pushAppsConfig.services
            assertThat(services).hasSize(1)

            val service = services[0]
            assertThat(service.name).isEqualTo("some-service-name")
            assertThat(service.plan).isEqualTo("a-good-one")
            assertThat(service.broker).isEqualTo("some-broker")
            assertThat(service.optional).isTrue()
        }

        it("Parses the security group config") {
            val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()

            val securityGroups = pushAppsConfig.securityGroups
            assertThat(securityGroups).hasSize(1)

            val securityGroup = securityGroups[0]
            assertThat(securityGroup.name).isEqualTo("some-group")
            assertThat(securityGroup.destination).isEqualTo("some-destination")
            assertThat(securityGroup.protocol).isEqualTo("all")
        }

        it("Parses the user provided services config") {
            val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()

            val userProvidedServices = pushAppsConfig.userProvidedServices
            assertThat(userProvidedServices).hasSize(1)

            val service = userProvidedServices[0]
            assertThat(service.name).isEqualTo("some-user-provided-service-name")
            assertThat(service.credentials).isEqualTo(mapOf("username" to "some-username"))
        }

        it("Parses the db migration config") {
            val pushAppsConfig = ConfigReader.parseConfig(findConfigPath("exampleConfig.yml")).get()

            val migrations = pushAppsConfig.migrations
            assertThat(migrations).hasSize(1)

            val migration = migrations[0]
            assertThat(migration.user).isEqualTo("user")
            assertThat(migration.password).isEqualTo("password")
            assertThat(migration.driver).isInstanceOfAny(DatabaseDriver.Postgres::class.java)
            assertThat(migration.host).isEqualTo("10.0.0.1")
            assertThat(migration.port).isEqualTo("5432")
            assertThat(migration.schema).isEqualTo("metrics")
            assertThat(migration.migrationDir).isEqualTo("/all/the/cool/migrations")
            assertThat(migration.repair).isFalse()
            assertThat(migration.placeholders).isEqualTo(mapOf("SOME_PLACEHOLDER" to "foo foo"))
        }
    }
})

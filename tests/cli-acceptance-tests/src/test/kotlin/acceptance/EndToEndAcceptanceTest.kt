package acceptance

import junit.framework.TestCase.assertTrue
import org.apache.commons.io.FilenameUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.cloudfoundry.client.v2.securitygroups.Protocol
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.GetApplicationRequest
import org.cloudfoundry.operations.routes.Level
import org.cloudfoundry.operations.routes.ListRoutesRequest
import org.cloudfoundry.tools.pushapps.*
import org.cloudfoundry.tools.pushapps.config.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import support.AcceptanceTestContext
import support.AcceptanceTestSupport
import support.DockerSupport

class EndToEndAcceptanceTest : Spek({
    describe("happy path") {
        context("when org, space, and security group don't exist") {
            val acceptanceTestSupport = AcceptanceTestSupport()

            val complimentService = UserProvidedServiceConfig(
                    name = "compliment-service",
                    credentials = mapOf("compliment" to "handsome")
            )

            val optionalService = ServiceConfig(
                    name = "optional-service",
                    plan = "some-plan",
                    broker = "something-that-does-not-exist",
                    optional = true
            )


            val helloAppJar = FilenameUtils.normalize(
                "${acceptanceTestSupport.acceptanceTestProjectDir}../../applications/hello/build/libs/hello.jar"
            )

            val helloApp = AppConfig(
                    name = "hello",
                    path = helloAppJar,
                    environment = mapOf(
                            "NAME" to "Steve",
                            "VERB" to "walk",
                            "ANIMAL_TYPE" to "dog",
                            "ANIMAL_NAME" to "Sally",
                            "HOUR" to "12",
                            "MINUTE" to "15"
                    ),
                    serviceNames = listOf(
                            "compliment-service",
                            "my-scheduler",
                            "optional-service"
                    )
            )

            val goodbyeAppJar = FilenameUtils.normalize(
                "${acceptanceTestSupport.acceptanceTestProjectDir}../../applications/goodbye/build/libs/goodbye.jar"
            )

            val blueGreenApp = AppConfig(
                    name = "generic",
                    path = goodbyeAppJar,
                    environment = mapOf(
                            "NAME" to "BLUE OR GREEN"
                    ),
                    noRoute = true,
                    domain = getCfDomain(),
                    route = Route(
                            hostname = "generic"
                    ),
                    blueGreenDeploy = true
            )

            val mysqlMigration = Migration(
                    host = acceptanceTestSupport.getEnvOrDefault("INTEGRATION_HOST", "127.0.0.1"),
                    port = "3338",
                    user = "root",
                    password = "supersecret",
                    schema = "new_db",
                    driver = DatabaseDriver.MySql(),
                    migrationDir = "${acceptanceTestSupport.acceptanceTestProjectDir}/src/test/kotlin/support/dbmigrations",
                    repair = false,
                    placeholders = emptyMap()
            )

            val pgMigration = Migration(
                    host = acceptanceTestSupport.getEnvOrDefault("INTEGRATION_HOST", "127.0.0.1"),
                    port = "6442",
                    user = "metrics",
                    password = "metrics_secret",
                    schema = "metrics",
                    driver = DatabaseDriver.Postgres(),
                    migrationDir = "${acceptanceTestSupport.acceptanceTestProjectDir}/src/test/kotlin/support/dbmigrations",
                    repair = false,
                    placeholders = emptyMap()
            )

            val securityGroup = SecurityGroup(
                    name = "some-name",
                    destination = "0.0.0.0-255.255.255.255",
                    protocol = "all"
            )

            var acceptanceTestContext: AcceptanceTestContext? = null

            afterEachTest {
                val tc = acceptanceTestContext
                if (tc !== null) {
                    acceptanceTestSupport.cleanupCf(tc, tc.organization, "test")
                }
            }

            it("pushes all the apps in the config, creates and binds services, creates org, space, and security groups, and runs migrations") {
                val docker = DockerSupport()

                acceptanceTestContext = acceptanceTestSupport.buildTestContext(
                    space = "test",
                    apps = listOf(helloApp, blueGreenApp),
                    services = listOf(optionalService),
                    userProvidedServices = listOf(complimentService),
                    migrations = listOf(mysqlMigration, pgMigration),
                    securityGroups = listOf(securityGroup),
                    retryCount = 0,
                    maxInFlight = 4
                )

                val tc = acceptanceTestContext
                if (tc === null) {
                    fail("Unable to build test context")
                    return@it
                }

                docker.startDocker()

                val mysqlConnection = docker.connectToMysql(mysqlMigration.host, mysqlMigration.port, mysqlMigration.user, mysqlMigration.password)
                if (mysqlConnection === null) {
                    fail("Unable to connect to MySql")
                    return@it
                }


                val pgConnection = docker.connectToPg(pgMigration.host, pgMigration.port, pgMigration.user, pgMigration.password)
                if (pgConnection === null) {
                    fail("Unable to connect to Postgres")
                    return@it
                }

                var organizations = tc.cfClient.listOrganizations().toIterable().toList()
                assertThat(organizations).doesNotContain(tc.organization)

                val exitCode = acceptanceTestSupport.runPushApps(tc.configFilePath)
                assertThat(exitCode).isEqualTo(0)

                val getHelloApplicationReq = GetApplicationRequest.builder().name("hello").build()
                val getGreenApplicationReq = GetApplicationRequest.builder().name("generic").build()
                val getBlueApplicationReq = GetApplicationRequest.builder().name("generic-blue").build()

                val targetedOperations = cloudFoundryOperationsBuilder()
                    .fromExistingOperations(tc.cfOperations)
                    .apply {
                        organization = tc.organization
                        space = "test"
                        skipSslValidation = true
                    }.build()

                val applicationOperations = targetedOperations
                    .applications()

                val helloUrl = applicationOperations
                    .get(getHelloApplicationReq)
                    .map({ it.urls[0] })
                    .toFuture()
                    .get()

                val helloResponse = acceptanceTestSupport.httpGet("http://$helloUrl").block()
                assertThat(helloResponse).contains("hello Steve, you are handsome!")
                assertThat(helloResponse).contains("compliment-service")
                assertThat(helloResponse).contains("my-scheduler")
                assertThat(helloResponse).contains("Did you remember to walk your dog named Sally at 12:15?")

                val greenUrl = applicationOperations
                    .get(getGreenApplicationReq)
                    .map({ it.urls[0] })
                    .toFuture()
                    .get()

                val greenResponse = acceptanceTestSupport.httpGet("http://$greenUrl").block()
                assertThat(greenResponse).contains("goodbye BLUE OR GREEN")

                val blueAppDetails = applicationOperations.get(getBlueApplicationReq).toFuture().get()
                assertThat(blueAppDetails.requestedState).isEqualTo("STOPPED")

                val listRoutesRequest = ListRoutesRequest.builder().level(Level.ORGANIZATION).build()
                val routes = targetedOperations
                    .routes()
                    .list(listRoutesRequest)
                    .filter { r ->
                        r.domain == blueGreenApp.domain &&
                            r.host == blueGreenApp.route!!.hostname
                    }
                    .toIterable()
                    .toList()

                assertThat(routes).hasSize(1)
                assertThat(routes[0].applications).containsOnly("generic")

                assertTrue("new_db DB exists in Mysql", docker.checkIfDatabaseExists(mysqlConnection, "new_db"))
                assertTrue("Test table 1 exists in Mysql", docker.checkIfTableExists(mysqlConnection, "new_db", "test_table_1"))
                assertTrue("Test table 2 exists in Mysql", docker.checkIfTableExists(mysqlConnection, "new_db", "test_table_2"))

                assertTrue("Metrics DB exists in Postgres", docker.checkIfDatabaseExists(pgConnection, "metrics"))
                assertTrue("Test table 1 exists in Postgres", docker.checkIfTableExists(pgConnection, "metrics", "test_table_1"))
                assertTrue("Test table 2 exists in Postgres", docker.checkIfTableExists(pgConnection, "metrics", "test_table_2"))

                organizations = tc.cfClient.listOrganizations().toIterable().toList()
                assertThat(organizations).contains(tc.organization)

                val reactiveCfClient = (tc.cfOperations as DefaultCloudFoundryOperations).cloudFoundryClient
                val cfSecurityGroups = acceptanceTestSupport.listSecurityGroupResources(reactiveCfClient, tc.securityGroups!!)

                assertThat(cfSecurityGroups).anySatisfy { group ->
                    assertThat(group.entity.name).isEqualTo(securityGroup.name)

                    assertThat(group.entity.rules).hasOnlyOneElementSatisfying { rule ->
                        assertThat(rule.protocol).isEqualTo(Protocol.ALL)
                        assertThat(rule.destination).isEqualTo(securityGroup.destination)
                    }
                }

                val spaces = tc.cfClient.listSpaces().toIterable().toList()
                assertThat(spaces).contains("test")
            }
        }
    }
})

private fun getCfDomain(): String {
    val env = System.getenv("CF_DOMAIN")
    if (env === null) {
        throw AssertionError("Must set CF_DOMAIN env var")
    }

    return env as String
}

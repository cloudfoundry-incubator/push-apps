package acceptance

import io.pivotal.pushapps.*
import io.pivotal.pushapps.CloudFoundryOperationsBuilder.Companion.cloudFoundryOperationsBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.cloudfoundry.client.v2.securitygroups.Protocol
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.GetApplicationRequest
import org.cloudfoundry.operations.routes.Level
import org.cloudfoundry.operations.routes.ListRoutesRequest
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import support.*

class EndToEndAcceptanceTest : Spek({
    describe("happy path") {
        context("when org, space, and security group don't exist") {
            val complimentService = UserProvidedServiceConfig(
                name = "compliment-service",
                credentials = mapOf("compliment" to "handsome")
            )

            val metricsForwarderService = ServiceConfig(
                name = "my-mf-service",
                plan = "unlimited",
                broker = "metrics-forwarder"
            )

            val optionalService = ServiceConfig(
                name = "optional-service",
                plan = "some-plan",
                broker = "something-that-does-not-exist",
                optional = true
            )

            val helloApp = AppConfig(
                name = "hello",
                path = "${workingDir}/src/test/kotlin/support/helloapp.zip",
                buildpack = "binary_buildpack",
                command = "./helloapp",
                environment = mapOf(
                    "NAME" to "Steve"
                ),
                serviceNames = listOf(
                    "compliment-service",
                    "my-mf-service",
                    "optional-service"
                )
            )

            val blueGreenApp = AppConfig(
                name = "generic",
                path = "${workingDir}/src/test/kotlin/support/goodbyeapp.zip",
                buildpack = "binary_buildpack",
                command = "./goodbyeapp",
                environment = mapOf(
                    "NAME" to "BLUE OR GREEN"
                ),
                noRoute = true,
                domain = "versace.gcp.pcf-metrics.com",
                route = Route(
                    hostname = "generic"
                ),
                blueGreenDeploy = true
            )

            val migration = Migration(
                host = getEnvOrDefault("INTEGRATION_HOST", "127.0.0.1"),
                port = "3338",
                user = "root",
                password = "supersecret",
                schema = "new_db",
                driver = DatabaseDriver.MySql(),
                migrationDir = "$workingDir/src/test/kotlin/support/dbmigrations"
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
                    cleanupCf(tc, tc.organization, "test")
                }
            }

            it("pushes all the apps in the config, creates and binds services, creates org, space, and security groups, and runs migrations") {
                println("starting test")
                acceptanceTestContext = buildTestContext(
                    space = "test",
                    apps = listOf(helloApp, blueGreenApp),
                    services = listOf(metricsForwarderService, optionalService),
                    userProvidedServices = listOf(complimentService),
                    migrations = listOf(migration),
                    securityGroups = listOf(securityGroup)
                )

                val tc = acceptanceTestContext
                if (tc === null) {
                    fail("Unable to build test context")
                    return@it
                }

                println("checkpoint about to start docker")
                startDocker()
                println("checkpoint docker started")

                val conn = connectToMysql(migration.host, migration.port, migration.user, migration.password)
                if (conn === null) {
                    fail("Unable to connect to MySql")
                    return@it
                }

                var organizations = tc.cfClient.listOrganizations()
                assertThat(organizations).doesNotContain(tc.organization)

                println("about to run push apps")
                val exitCode = runPushApps(tc.configFilePath)
                println("push apps finished")
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

                val helloResponse = httpGet("http://$helloUrl/v1")
                val helloResponseBody = helloResponse.body()?.string()
                assertThat(helloResponse.isSuccessful).isTrue()
                assertThat(helloResponseBody).contains("hello Steve, you are handsome!")
                assertThat(helloResponseBody).contains("compliment-service")
                assertThat(helloResponseBody).contains("my-mf-service")

                val greenUrl = applicationOperations
                    .get(getGreenApplicationReq)
                    .map({ it.urls[0] })
                    .toFuture()
                    .get()

                val greenResponse = httpGet("http://$greenUrl")
                assertThat(greenResponse.isSuccessful).isTrue()
                assertThat(greenResponse.body()?.string()).contains("goodbye BLUE OR GREEN")

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

                assertThat(checkIfDatabaseExists(conn, "new_db")).isTrue()
                assertThat(checkIfTableExists(conn, "new_db", "test_table_1")).isTrue()
                assertThat(checkIfTableExists(conn, "new_db", "test_table_2")).isTrue()

                organizations = tc.cfClient.listOrganizations()
                assertThat(organizations).contains(tc.organization)

                val reactiveCfClient = (tc.cfOperations as DefaultCloudFoundryOperations).cloudFoundryClient
                val cfSecurityGroups = listSecurityGroupResources(reactiveCfClient, tc.securityGroups!!)

                assertThat(cfSecurityGroups).anySatisfy { group ->
                    assertThat(group.entity.name).isEqualTo(securityGroup.name)

                    assertThat(group.entity.rules).hasOnlyOneElementSatisfying { rule ->
                        assertThat(rule.protocol).isEqualTo(Protocol.ALL)
                        assertThat(rule.destination).isEqualTo(securityGroup.destination)
                    }
                }

                val spaces = tc.cfClient.listSpaces()
                assertThat(spaces).contains("test")
            }
        }
    }
})

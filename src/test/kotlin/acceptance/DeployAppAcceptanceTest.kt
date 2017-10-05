package acceptance

import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.operations.applications.GetApplicationRequest
import org.cloudfoundry.operations.routes.Level
import org.cloudfoundry.operations.routes.ListRoutesRequest
import pushapps.*
import support.*

class DeployAppAcceptanceTest : Test({
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
        path = "$workingDir/src/test/kotlin/support/helloapp.zip",
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

    val goodbyeApp = AppConfig(
        name = "goodbye",
        path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
        buildpack = "binary_buildpack",
        command = "./goodbyeapp",
        noRoute = true,
        domain = "versace.gcp.pcf-metrics.com",
        route = Route(
            hostname = "oranges",
            path = "/v1"
        ),
        environment = mapOf(
            "NAME" to "George"
        )
    )

    val blueGreenApp = AppConfig(
        name = "generic",
        path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
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

    describe("pushApps interacts with applications by") {
        test("pushing every application in the config file") {
            val tc = buildTestContext(
                organization = "dewey",
                space = "test",
                apps = listOf(helloApp, goodbyeApp),
                services = listOf(metricsForwarderService, optionalService),
                userProvidedServices = listOf(complimentService)
            )

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)

            val getHelloApplicationReq = GetApplicationRequest.builder().name("hello").build()
            val getGoodbyeApplicationReq = GetApplicationRequest.builder().name("goodbye").build()

            val targetedOperations = cloudFoundryOperationsBuilder()
                .fromExistingOperations(tc.cfOperations)
                .apply {
                    organization = "dewey"
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

            val goodbyeUrl = applicationOperations
                .get(getGoodbyeApplicationReq)
                .map({ it.urls[0] })
                .toFuture()
                .get()

            assertThat(goodbyeUrl).contains("oranges")

            val goodbyeResponse = httpGet("http://$goodbyeUrl/v1")
            assertThat(goodbyeResponse.isSuccessful).isTrue()
            assertThat(goodbyeResponse.body()?.string()).contains("goodbye from v1 George")

            cleanupCf(tc, "dewey", "test")
        }

        test("blue green deploys applications with blue green set to true") {
            val tc = buildTestContext(
                organization = "dewey",
                space = "test",
                apps = listOf(blueGreenApp)
            )

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)

            val getGreenApplicationReq = GetApplicationRequest.builder().name("generic").build()
            val getBlueApplicationReq = GetApplicationRequest.builder().name("generic-blue").build()

            val targetedOperations = cloudFoundryOperationsBuilder()
                .fromExistingOperations(tc.cfOperations)
                .apply {
                    organization = "dewey"
                    space = "test"
                    skipSslValidation = true
                }.build()

            val applicationOperations = targetedOperations
                .applications()

            val greenUrl = applicationOperations
                .get(getGreenApplicationReq)
                .map({ it.urls[0] })
                .toFuture()
                .get()

            val helloResponse = httpGet("http://$greenUrl")
            assertThat(helloResponse.isSuccessful).isTrue()
            assertThat(helloResponse.body()?.string()).contains("goodbye BLUE OR GREEN")

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

            cleanupCf(tc, "dewey", "test")
        }

        test("returning an error if a deploy fails") {
            val badBuildpackApp = AppConfig(
                name = "sample",
                path = "$workingDir/src/test/kotlin/support/sample-app.zip",
                buildpack = "im_not_really_a_buildpack",
                command = "sample-app/sample-app",
                environment = mapOf(
                    "NAME" to "Steve"
                )
            )
            val tc = buildTestContext(
                organization = "dewey",
                space = "test",
                apps = listOf(badBuildpackApp)
            )

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(3)

            cleanupCf(tc, "dewey", "test")
        }
    }
})

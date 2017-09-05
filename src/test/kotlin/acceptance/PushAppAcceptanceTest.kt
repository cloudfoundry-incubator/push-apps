package acceptance

import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.operations.applications.GetApplicationRequest
import pushapps.AppConfig
import pushapps.cloudFoundryOperationsBuilder
import support.*

class PushAppAcceptanceTest : Test({
    val helloApp = AppConfig(
        name = "hello",
        path = "$workingDir/src/test/kotlin/support/helloapp.zip",
        buildpack = "binary_buildpack",
        command = "./helloapp",
        environment = mapOf(
            "NAME" to "Steve"
        )
    )

    val goodbyeApp = AppConfig(
        name = "goodbye",
        path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
        buildpack = "binary_buildpack",
        command = "./goodbyeapp",
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
        blueGreenDeploy = true
    )

    describe("pushApps interacts with applications by") {
        test("pushing every application in the config file") {
            val tc = buildTestContext("dewey", "test", listOf(helloApp, goodbyeApp))

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)

            val getHelloApplicationReq = GetApplicationRequest.builder().name("hello").build()
            val getGoodbyeApplicationReq = GetApplicationRequest.builder().name("goodbye").build()

            val targetedOperations = cloudFoundryOperationsBuilder()
                .fromExistingOperations(tc.cfOperations)
                .apply {
                    organization = "dewey"
                    space = "test"
                }.build()

            val applicationOperations = targetedOperations
                .applications()

            val helloUrl = applicationOperations
                .get(getHelloApplicationReq)
                .map({ it.urls[0] })
                .toFuture()
                .get()

            val helloResponse = httpGet("http://$helloUrl")
            assertThat(helloResponse.isSuccessful).isTrue()
            assertThat(helloResponse.body()?.string()).contains("hello Steve")

            val goodbyeUrl = applicationOperations
                .get(getGoodbyeApplicationReq)
                .map({ it.urls[0] })
                .toFuture()
                .get()

            val goodbyeResponse = httpGet("http://$goodbyeUrl")
            assertThat(goodbyeResponse.isSuccessful).isTrue()
            assertThat(goodbyeResponse.body()?.string()).contains("goodbye George")

            cleanupCf(tc, "dewey", "test")
        }

        test("blue green deploys applications with blue green set to true") {
            val tc = buildTestContext("dewey", "test", listOf(blueGreenApp))

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)

            val getGreenApplicationReq = GetApplicationRequest.builder().name("generic").build()
            val getBlueApplicationReq = GetApplicationRequest.builder().name("generic-blue").build()

            val targetedOperations = cloudFoundryOperationsBuilder()
                .fromExistingOperations(tc.cfOperations)
                .apply {
                    organization = "dewey"
                    space = "test"
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
            val tc = buildTestContext("dewey", "test", listOf(badBuildpackApp))

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(3)

            cleanupCf(tc, "dewey", "test")
        }
    }
})
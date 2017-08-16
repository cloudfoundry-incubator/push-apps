package acceptance

import acceptance.support.*
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.operations.applications.GetApplicationRequest
import pushapps.AppConfig
import pushapps.cloudFoundryOperationsBuilder
import reactor.core.publisher.SynchronousSink

class PushAppAcceptanceTest : Test({
    val sampleApp = AppConfig(
        name = "sample",
        path = "$workingDir/src/test/kotlin/acceptance/support/sample-app.zip",
        buildpack = "binary_buildpack",
        command = "sample-app/sample-app",
        environment = mapOf(
            "NAME" to "Steve"
        )
    )

    describe("pushApps interacts with applications by") {
        test("pushing every application in the config file") {
            val tc = buildTestContext("dewey", "test", listOf(sampleApp))

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(0)

            val getApplicationRequest = GetApplicationRequest.builder().name("sample").build()
            val targetedOperations = cloudFoundryOperationsBuilder()
                .fromExistingOperations(tc.cfOperations)
                .apply {
                    organization = "dewey"
                    space = "test"
                }.build()


            targetedOperations
                .applications()
                .get(getApplicationRequest)
                .map({ it.urls[0] })
                .handle({ url, sink: SynchronousSink<String> ->
                    val response = httpGet("http://$url")

                    assertThat(response.isSuccessful).isTrue()
                    assertThat(response.body()?.string()).contains("hello Steve")

                    sink.complete()
                }).block()

            cleanupCf(tc, "dewey", "test")
        }

        test("returning an error if a deploy fails") {
            val badBuildpackApp = AppConfig(
                name = "sample",
                path = "$workingDir/src/test/kotlin/acceptance/support/sample-app.zip",
                buildpack = "im_not_really_a_buildpack",
                command = "sample-app/sample-app",
                environment = mapOf(
                    "NAME" to "Steve"
                )
            )
            val tc = buildTestContext("dewey", "test", listOf(badBuildpackApp))

            val exitCode = runPushApps(tc.configFilePath)
            assertThat(exitCode).isEqualTo(127)

            cleanupCf(tc, "dewey", "test")
        }
    }
})
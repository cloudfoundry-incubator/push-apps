package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.Applications
import pushapps.AppConfig
import pushapps.CloudFoundryClient
import reactor.core.publisher.Mono

data class CloudFoundryClientTestContext(
    val cloudFoundryClient: CloudFoundryClient,
    val mockApplications: Applications
)

fun buildCloudFoundryClientTestContext(): CloudFoundryClientTestContext {
    val mockApplications = mock<Applications>()
    whenever(mockApplications.setEnvironmentVariable(any())).thenReturn(Mono.empty())

    val mockCfOperations = mock<CloudFoundryOperations>()
    whenever(mockCfOperations.applications()).thenReturn(mockApplications)

    val cloudFoundryClient = CloudFoundryClient(
        apiHost = "api.host",
        username = "username",
        password = "password",
        cloudFoundryOperations = mockCfOperations)

    return CloudFoundryClientTestContext(cloudFoundryClient, mockApplications)
}

class CloudFoundryClientTest : Test({
    describe("#setApplicationEnvironment") {
        test("sets environment variables if present in config") {
            val tc = buildCloudFoundryClientTestContext()
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                environment = mapOf(
                    "FOO" to "BAR",
                    "BAR" to "BAZ"
                )
            )

            tc.cloudFoundryClient.setApplicationEnvironment(appConfig)

            verify(tc.mockApplications, times(1)).setEnvironmentVariable(
                argForWhich {
                    name == appConfig.name &&
                        variableName == "FOO" &&
                        variableValue == "BAR"
                }
            )

            verify(tc.mockApplications, times(1)).setEnvironmentVariable(
                argForWhich {
                    name == appConfig.name &&
                        variableName == "BAR" &&
                        variableValue == "BAZ"
                }
            )

        }

        test("does not set environment variables if none in config") {
            val tc = buildCloudFoundryClientTestContext()
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                environment = mapOf()
            )

            tc.cloudFoundryClient.setApplicationEnvironment(appConfig)

            verify(tc.mockApplications, times(0)).setEnvironmentVariable(any())
        }
    }
})
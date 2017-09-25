package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import pushapps.*
import reactor.core.publisher.Mono
import org.assertj.core.api.Assertions.assertThat

class AppDeployerTest : Test({
    data class TestContext(
        val appDeployer: AppDeployer,
        val mockCfClient: CloudFoundryClient
    )

    fun buildTestContext(appConfig: AppConfig, retryCount: Int): TestContext {
        val mockCfClient = mock<CloudFoundryClient>()

        val appDeployer = AppDeployer(
            cloudFoundryClient = mockCfClient,
            appConfigs = listOf(appConfig),
            retryCount = retryCount
        )

        return TestContext(appDeployer, mockCfClient)
    }


    describe("#deployApps") {
        test("Deploys the apps") {
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                buildpack = "bob_the_builder",
                environment = mapOf("LEMONS" to "LIMES"),
                serviceNames = listOf("grapefruit"),
                route = Route("kiwi", "orange")
            )
            val tc = buildTestContext(appConfig = appConfig, retryCount = 1)

            whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(listOf(Mono.empty()))
            whenever(tc.mockCfClient.bindServicesToApplication(any())).thenReturn(listOf(Mono.empty()))
            whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty())

            tc.appDeployer.deployApps()

            verify(tc.mockCfClient, times(1)).pushApplication(appConfig)
            verify(tc.mockCfClient, times(1)).setApplicationEnvironment(appConfig)
            verify(tc.mockCfClient, times(1)).bindServicesToApplication(appConfig)
            verify(tc.mockCfClient, times(1)).startApplication(appConfig.name)
            verify(tc.mockCfClient, times(1)).mapRoute(appConfig)
        }

        //TODO blue green

        //TODO single failure

        test("Retries Deploying Failed App") {
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                buildpack = "bob_the_builder",
                environment = mapOf("LEMONS" to "LIMES"),
                serviceNames = listOf("grapefruit"),
                route = Route("kiwi", "orange")
            )
            val tc = buildTestContext(appConfig = appConfig, retryCount = 2)

            whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(listOf(Mono.empty()))
            whenever(tc.mockCfClient.bindServicesToApplication(any()))
                .thenReturn(listOf(Mono.error(Error("lemons"))))
                .thenReturn(listOf(Mono.empty()))

            whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty())

            tc.appDeployer.deployApps()

            verify(tc.mockCfClient, times(2)).pushApplication(appConfig)
            verify(tc.mockCfClient, times(2)).setApplicationEnvironment(appConfig)
            verify(tc.mockCfClient, times(2)).bindServicesToApplication(appConfig)
            verify(tc.mockCfClient, times(1)).startApplication(appConfig.name)
            verify(tc.mockCfClient, times(1)).mapRoute(appConfig)
        }
    }
})
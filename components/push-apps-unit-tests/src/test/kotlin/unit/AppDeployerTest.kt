package unit

import com.nhaarman.mockito_kotlin.*
import io.pivotal.pushapps.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.doppler.LogMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AppDeployerTest : Spek({
    data class TestContext(
        val appDeployer: AppDeployer,
        val mockCfClient: CloudFoundryClient
    )

    fun buildTestContext(appConfig: AppConfig, retryCount: Int): TestContext {
        val mockCfClient = mock<CloudFoundryClient>()
        val logMessage = mock<LogMessage>()
        whenever(mockCfClient.fetchRecentLogsForAsync(any())).thenReturn(Flux.fromIterable(listOf(logMessage)))

        val appDeployer = AppDeployer(
            cloudFoundryClient = mockCfClient,
            appConfigs = listOf(appConfig),
            retryCount = retryCount,
            maxInFlight = 1,
            availableServices = listOf("grapefruit")
        )

        return TestContext(appDeployer, mockCfClient)
    }

    describe("#deployApps") {
        it("Deploys the apps") {
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
            whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.bindServicesToApplication(any(), any())).thenReturn(listOf(Mono.empty()))
            whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty())

            val results = tc.appDeployer.deployApps()

            verify(tc.mockCfClient, times(1)).pushApplication(appConfig)
            verify(tc.mockCfClient, times(1)).setApplicationEnvironment(appConfig)
            verify(tc.mockCfClient, times(1)).bindServicesToApplication(appConfig.name, appConfig.serviceNames!!)
            verify(tc.mockCfClient, times(1)).startApplication(appConfig.name)
            verify(tc.mockCfClient, times(1)).mapRoute(appConfig)

            assertThat(results).hasSize(5)

            val allOperationsWereSuccessful = results.fold(true) { memo, result -> memo && result.didSucceed}
            assertThat(allOperationsWereSuccessful).isTrue()
        }

        it("Deploys the app despite unavailable services") {
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                buildpack = "bob_the_builder",
                environment = mapOf("LEMONS" to "LIMES"),
                serviceNames = listOf("unavailable"),
                route = Route("kiwi", "orange")
            )
            val tc = buildTestContext(appConfig = appConfig, retryCount = 1)

            whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty())

            tc.appDeployer.deployApps()

            verify(tc.mockCfClient, times(1)).pushApplication(appConfig)
            verify(tc.mockCfClient, times(1)).setApplicationEnvironment(appConfig)
            verify(tc.mockCfClient, times(1)).bindServicesToApplication(appConfig.name, emptyList())
            verify(tc.mockCfClient, times(1)).startApplication(appConfig.name)
            verify(tc.mockCfClient, times(1)).mapRoute(appConfig)
        }

        it("Returns a failed operation result when an operation fails, without losing successful results") {
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                buildpack = "bob_the_builder",
                environment = mapOf("LEMONS" to "LIMES"),
                serviceNames = listOf("grapefruit"),
                route = Route("kiwi", "orange")
            )
            val tc = buildTestContext(appConfig = appConfig, retryCount = 0)

            whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.bindServicesToApplication(any(), any())).thenReturn(listOf(Mono.empty()))
            whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty())
            whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.error(Error("it broke")))

            val results = tc.appDeployer.deployApps()

            assertThat(results).hasSize(5)

            val allOperationsWereSuccessful = results.fold(true) { memo, result -> memo && result.didSucceed}
            assertThat(allOperationsWereSuccessful).isFalse()

            val failedOperationCount = results.fold(0) { memo, result -> if (result.didSucceed) memo else memo + 1 }
            assertThat(failedOperationCount).isEqualTo(1)

            val failedOperation: OperationResult? = results.find { !it.didSucceed }
            assertThat(failedOperation).isNotNull()
            assertThat(failedOperation!!.name).isEqualTo("Push application Foo bar")
        }

        //TODO blue green
    }
})

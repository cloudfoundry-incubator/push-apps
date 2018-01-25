package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.tools.pushapps.config.AppConfig
import org.cloudfoundry.tools.pushapps.CloudFoundryClient
import org.cloudfoundry.tools.pushapps.OperationResult
import org.cloudfoundry.tools.pushapps.config.Route
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AppDeployerTest : Spek({
    data class TestContext(
        val appDeployer: org.cloudfoundry.tools.pushapps.AppDeployer,
        val mockCfClient: CloudFoundryClient
    )

    fun buildTestContext(appConfig: AppConfig, retryCount: Int): TestContext {
        val mockCfClient = mock<CloudFoundryClient>()
        val logMessage = mock<LogMessage>()
        whenever(mockCfClient.fetchRecentLogsForAsync(any())).thenReturn(Flux.fromIterable(listOf(logMessage)), Flux.fromIterable(listOf(logMessage)))

        val appDeployer = org.cloudfoundry.tools.pushapps.AppDeployer(
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

            val results = tc.appDeployer.deployApps().toIterable().toList()
            assertThat(results).hasSize(5)

            verify(tc.mockCfClient, times(1)).pushApplication(appConfig)
            verify(tc.mockCfClient, times(1)).setApplicationEnvironment(appConfig)
            verify(tc.mockCfClient, times(1)).bindServicesToApplication(appConfig.name, appConfig.serviceNames)
            verify(tc.mockCfClient, times(1)).startApplication(appConfig.name)
            verify(tc.mockCfClient, times(1)).mapRoute(appConfig)

            val allOperationsWereSuccessful = results.fold(true) { memo, result -> memo && result.didSucceed }
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

            tc.appDeployer.deployApps().toIterable().toList()

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

            val results = tc.appDeployer.deployApps().toIterable().toList()

            assertThat(results).hasSize(5)

            val allOperationsWereSuccessful = results.fold(true) { memo, result -> memo && result.didSucceed }
            assertThat(allOperationsWereSuccessful).isFalse()

            val failedOperationCount = results.fold(0) { memo, result -> if (result.didSucceed) memo else memo + 1 }
            assertThat(failedOperationCount).isEqualTo(1)

            val failedOperation: OperationResult? = results.find { !it.didSucceed }
            assertThat(failedOperation).isNotNull()
            assertThat(failedOperation!!.description).isEqualTo("Push application Foo bar")
        }

        context("blue green deployments") {
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                buildpack = "bob_the_builder",
                environment = mapOf("LEMONS" to "LIMES"),
                serviceNames = listOf("grapefruit"),
                route = Route("kiwi", "orange"),
                blueGreenDeploy = true
            )

            val greenAppConfig = appConfig.copy(noRoute = true)
            val blueAppConfig = greenAppConfig.copy(name = "${greenAppConfig.name}-blue")
            val tc = buildTestContext(appConfig = appConfig, retryCount = 1)

            beforeEachTest {
                reset(tc.mockCfClient)
                whenever(tc.mockCfClient.listApplications()).thenReturn(Flux.fromIterable(listOf(appConfig.name)))
                whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.bindServicesToApplication(any(), any())).thenReturn(listOf(Mono.empty()), listOf(Mono.empty()))
                whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.unmapRoute(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.stopApplication(any())).thenReturn(Mono.empty())
            }

            it("deploys both the regular app, as well as a blue one") {
                val results = tc.appDeployer.deployApps().toIterable().toList()
                assertThat(results).hasSize(13)

                verify(tc.mockCfClient, times(1)).pushApplication(greenAppConfig)
                verify(tc.mockCfClient, times(1)).pushApplication(blueAppConfig)

                verify(tc.mockCfClient, times(1)).setApplicationEnvironment(greenAppConfig)
                verify(tc.mockCfClient, times(1)).setApplicationEnvironment(blueAppConfig)

                verify(tc.mockCfClient, times(1)).bindServicesToApplication(greenAppConfig.name, greenAppConfig.serviceNames)
                verify(tc.mockCfClient, times(1)).bindServicesToApplication(blueAppConfig.name, blueAppConfig.serviceNames)

                verify(tc.mockCfClient, times(1)).startApplication(greenAppConfig.name)
                verify(tc.mockCfClient, times(1)).startApplication(blueAppConfig.name)

                verify(tc.mockCfClient, times(1)).mapRoute(greenAppConfig)
                verify(tc.mockCfClient, times(1)).mapRoute(blueAppConfig)

                verify(tc.mockCfClient, times(1)).unmapRoute(greenAppConfig)
                verify(tc.mockCfClient, times(1)).unmapRoute(blueAppConfig)

                verify(tc.mockCfClient, times(1)).stopApplication(blueAppConfig.name)

                val allOperationsWereSuccessful = results.fold(true) { memo, result -> memo && result.didSucceed }
                assertThat(allOperationsWereSuccessful).isTrue()
            }

            it("deploys each app with noRoute set to true") {
                tc.appDeployer.deployApps().toIterable().toList()

                verify(tc.mockCfClient, times(1)).pushApplication(argForWhich {
                    name == appConfig.name && noRoute
                })
                verify(tc.mockCfClient, times(1)).pushApplication(argForWhich {
                    name == blueAppConfig.name && noRoute
                })
            }
        }
    }
})

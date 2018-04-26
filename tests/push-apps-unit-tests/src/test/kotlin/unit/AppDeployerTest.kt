package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.tools.pushapps.AppDeployer
import org.cloudfoundry.tools.pushapps.CloudFoundryClient
import org.cloudfoundry.tools.pushapps.OperationResult
import org.cloudfoundry.tools.pushapps.config.AppConfig
import org.cloudfoundry.tools.pushapps.config.Route
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.temporal.ChronoUnit

class AppDeployerTest : Spek({
    data class TestContext(
        val appDeployer: AppDeployer,
        val mockCfClient: CloudFoundryClient
    )

    fun buildTestContext(appConfigs: List<AppConfig>, maxInFlight: Int = 1, existingApplications: List<String> = emptyList()): TestContext {
        val mockCfClient = mock<CloudFoundryClient>()
        val logMessage = mock<LogMessage>()
        whenever(mockCfClient.fetchRecentLogsForAsync(any())).thenReturn(Flux.fromIterable(listOf(logMessage)), Flux.fromIterable(listOf(logMessage)))

        val appDeployer = AppDeployer(
            cloudFoundryClient = mockCfClient,
            appConfigs = appConfigs,
            availableServices = listOf("grapefruit", "rags"),
            existingApplications = existingApplications,
            maxInFlight = maxInFlight
        )

        return TestContext(appDeployer, mockCfClient)
    }

    fun buildTestContext(appConfig: AppConfig, maxInFlight: Int = 1, existingApplications: List<String> = emptyList()): TestContext {
        return buildTestContext(listOf(appConfig), maxInFlight, existingApplications)
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
            val tc = buildTestContext(appConfig = appConfig)

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
            val tc = buildTestContext(appConfig = appConfig)

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
            val tc = buildTestContext(appConfig = appConfig)

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
            val tc = buildTestContext(appConfig = appConfig, existingApplications = listOf(appConfig.name))

            beforeEachTest {
                reset(tc.mockCfClient)
                whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.bindServicesToApplication(any(), any())).thenReturn(listOf(Mono.empty()), listOf(Mono.empty()))
                whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.unmapRoute(any())).thenReturn(Mono.empty(), Mono.empty())
                whenever(tc.mockCfClient.stopApplication(any())).thenReturn(Mono.empty())
            }

            it("deploys both the regular app, as well as a blue one") {
                val results = tc.appDeployer
                    .deployApps()
                    .timeout(Duration.of(5, ChronoUnit.SECONDS))
                    .toIterable()
                    .toList()
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

        context("handling errors") {
            it("catches thrown exceptions and returns the appropriate operation result") {
                val appConfig = AppConfig(
                    name = "Foo bar",
                    path = "/tmp/foo/bar",
                    buildpack = "bob_the_builder",
                    environment = mapOf("LEMONS" to null),
                    serviceNames = listOf("grapefruit"),
                    route = Route("kiwi", "orange")
                )
                val tc = buildTestContext(appConfig = appConfig)

                whenever(tc.mockCfClient.pushApplication(any())).thenReturn(Mono.empty())
                whenever(tc.mockCfClient.setApplicationEnvironment(any())).thenThrow(NullPointerException())
                whenever(tc.mockCfClient.bindServicesToApplication(any(), any())).thenReturn(listOf(Mono.empty()))
                whenever(tc.mockCfClient.startApplication(any())).thenReturn(Mono.empty())
                whenever(tc.mockCfClient.mapRoute(any())).thenReturn(Mono.empty())

                val results = tc.appDeployer
                    .deployApps()
                    .timeout(Duration.of(10, ChronoUnit.SECONDS))
                    .toIterable()
                    .toList()

                assertThat(results).hasSize(2)

                verify(tc.mockCfClient, times(1)).pushApplication(appConfig)
                verify(tc.mockCfClient, times(1)).setApplicationEnvironment(appConfig)

                val allOperationsWereSuccessful = results.fold(true) { memo, result -> memo && result.didSucceed }
                assertThat(allOperationsWereSuccessful).isFalse()

                val failedOperation = results.find { result -> !result.didSucceed }
                    ?: return@it fail("Expected a failed operation result")

                assertThat(failedOperation.description).isEqualTo("Push application ${appConfig.name}")
            }
        }
    }
})

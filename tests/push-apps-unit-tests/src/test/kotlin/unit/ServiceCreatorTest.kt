package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.pushapps.CloudFoundryClient
import org.cloudfoundry.pushapps.ServiceConfig
import org.cloudfoundry.pushapps.ServiceCreator
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ServiceCreatorTest : Spek({
    data class TestContext(
        val serviceCreator: ServiceCreator,
        val mockCloudFoundryClient: CloudFoundryClient,
        val serviceConfig: ServiceConfig
    )

    fun buildTestContext(): TestContext {
        val mockCloudFoundryClient = mock<CloudFoundryClient>()

        val serviceConfig = ServiceConfig(
            name = "some-service",
            plan = "some-plan",
            broker = "some-broker",
            optional = true
        )

        val serviceCreator = ServiceCreator(
            cloudFoundryClient = mockCloudFoundryClient,
            serviceConfigs = listOf(serviceConfig),
            maxInFlight = 1,
            retryCount = 0
        )

        return TestContext(serviceCreator, mockCloudFoundryClient, serviceConfig)
    }

    describe("#createServices") {
        it("it creates the services and returns a success result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(emptyList()))
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createServices().toIterable().toList()
            assertThat(results).hasSize(1)

            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            val firstResult = results[0]
            assertThat(firstResult.description).isEqualTo("Create service ${tc.serviceConfig.name}")
            assertThat(firstResult.didSucceed).isTrue()

        }

        it("it returns a failure result when creating a service fails") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(emptyList()))
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(
                Mono.fromSupplier { throw Exception("lemons") }
            )

            val results = tc.serviceCreator.createServices().toIterable().toList()
            assertThat(results).hasSize(1)

            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            val result = results[0]
            assertThat(result.didSucceed).isFalse()
            assertThat(result.description).isEqualTo("Create service ${tc.serviceConfig.name}")
            assertThat(result.error!!.message).contains("lemons")
        }

        it("it includes whether the service was optional in the result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(emptyList()))
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(
                Mono.fromSupplier { throw Exception("lemons") }
            )

            val results = tc.serviceCreator.createServices().toIterable().toList()
            assertThat(results).hasSize(1)

            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            val result = results[0]
            assertThat(result.operationConfig.optional).isTrue()
        }

        it("it does not try to create services that already exist") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(listOf(tc.serviceConfig.name)))

            val results = tc.serviceCreator.createServices().toIterable().toList()
            assertThat(results).isEmpty()

            verify(tc.mockCloudFoundryClient, times(0)).createService(any())
        }
    }
})

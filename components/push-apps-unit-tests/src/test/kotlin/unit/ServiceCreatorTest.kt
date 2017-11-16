package unit

import com.nhaarman.mockito_kotlin.*
import io.pivotal.pushapps.CloudFoundryClient
import io.pivotal.pushapps.ServiceConfig
import io.pivotal.pushapps.ServiceCreator
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
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
            serviceConfigs = listOf(serviceConfig))

        return TestContext(serviceCreator, mockCloudFoundryClient, serviceConfig)
    }

    describe("#createServices") {
        it("it creates the services and returns a success result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(emptyList())
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            assertThat(results).hasSize(1)

            val firstResult = results[0]
            assertThat(firstResult.name).isEqualTo(tc.serviceConfig.name)
            assertThat(firstResult.didSucceed).isTrue()

        }

        it("it returns a failure result when creating a service fails") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(emptyList())
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(
                Mono.fromSupplier { throw Exception("lemons") }
            )

            val results = tc.serviceCreator.createServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            Assertions.assertThat(results).hasSize(1)
            val result = results[0]
            Assertions.assertThat(result.didSucceed).isFalse()
            Assertions.assertThat(result.name).isEqualTo(tc.serviceConfig.name)
            Assertions.assertThat(result.error!!.message).contains("lemons")
        }

        it("it includes whether the service was optional in the result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(emptyList())
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(
                Mono.fromSupplier { throw Exception("lemons") }
            )

            val results = tc.serviceCreator.createServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            Assertions.assertThat(results).hasSize(1)
            val result = results[0]
            Assertions.assertThat(result.optional).isTrue()
        }

        it("it does not try to create services that already exist") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(listOf(tc.serviceConfig.name))

            val results = tc.serviceCreator.createServices()
            verify(tc.mockCloudFoundryClient, times(0))
                .createService(any())

            Assertions.assertThat(results).isEmpty()
        }
    }
})

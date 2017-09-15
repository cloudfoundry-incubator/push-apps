package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import org.assertj.core.api.Assertions
import pushapps.*
import reactor.core.publisher.Mono

class ServiceCreatorTest : Test({
    data class TestContext(
        val serviceCreator: ServiceCreator,
        val mockCloudFoundryClient: CloudFoundryClient,
        val serviceConfig: ServiceConfig
    )

    fun buildTestContext(): TestContext {
        val mockCloudFoundryClient = mock<CloudFoundryClient>()

        val serviceConfig = ServiceConfig(name = "some-service", plan = "some-plan", broker = "some-broker")
        val serviceCreator = ServiceCreator(
            cloudFoundryClient = mockCloudFoundryClient,
            serviceConfigs = listOf(serviceConfig))

        return TestContext(serviceCreator, mockCloudFoundryClient, serviceConfig)
    }

    describe("#createServices") {
        test("it creates the services and returns a success result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(emptyList())
            whenever(tc.mockCloudFoundryClient.createService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .createService(tc.serviceConfig)

            Assertions.assertThat(results).containsOnly(OperationResult(tc.serviceConfig.name, didSucceed = true))
        }

        test("it returns a failure result when creating a service fails") {
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

        test("it does not try to create services that already exist") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(listOf(tc.serviceConfig.name))

            val results = tc.serviceCreator.createServices()
            verify(tc.mockCloudFoundryClient, times(0))
                .createService(any())

            Assertions.assertThat(results).isEmpty()
        }
    }
})
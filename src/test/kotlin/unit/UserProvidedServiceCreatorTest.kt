package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import io.pivotal.pushapps.CloudFoundryClient
import io.pivotal.pushapps.OperationResult
import io.pivotal.pushapps.UserProvidedServiceConfig
import io.pivotal.pushapps.UserProvidedServiceCreator
import reactor.core.publisher.Mono

class UserProvidedServiceCreatorTest : Test({
    data class TestContext(
        val serviceCreator: UserProvidedServiceCreator,
        val mockCloudFoundryClient: CloudFoundryClient,
        val serviceConfig: UserProvidedServiceConfig
    )

    fun buildTestContext(): TestContext {
        val mockCloudFoundryClient = mock<CloudFoundryClient>()

        val serviceConfig = UserProvidedServiceConfig(credentials = emptyMap(), name = "some-service")
        val serviceCreator = UserProvidedServiceCreator(
            cloudFoundryClient = mockCloudFoundryClient,
            serviceConfigs = listOf(serviceConfig))

        return TestContext(serviceCreator, mockCloudFoundryClient, serviceConfig)
    }

    describe("#createOrUpdateServices") {
        test("it creates the services and returns a success result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(emptyList())
            whenever(tc.mockCloudFoundryClient.createUserProvidedService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createOrUpdateServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .createUserProvidedService(tc.serviceConfig)

            assertThat(results).containsOnly(OperationResult(tc.serviceConfig.name, didSucceed = true))
        }

        test("it returns a failure result when creating a service fails") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(emptyList())
            whenever(tc.mockCloudFoundryClient.createUserProvidedService(any())).thenReturn(
                Mono.fromSupplier { throw Exception("lemons") }
            )

            val results = tc.serviceCreator.createOrUpdateServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .createUserProvidedService(tc.serviceConfig)

            assertThat(results).hasSize(1)
            val result = results[0]
            assertThat(result.didSucceed).isFalse()
            assertThat(result.name).isEqualTo(tc.serviceConfig.name)
            assertThat(result.error!!.message).contains("lemons")
        }

        test("it updates services that already exist") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(listOf(tc.serviceConfig.name))
            whenever(tc.mockCloudFoundryClient.updateUserProvidedService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createOrUpdateServices()
            verify(tc.mockCloudFoundryClient, times(1))
                .updateUserProvidedService(tc.serviceConfig)

            assertThat(results).containsOnly(OperationResult(tc.serviceConfig.name, didSucceed = true))
        }
    }
})

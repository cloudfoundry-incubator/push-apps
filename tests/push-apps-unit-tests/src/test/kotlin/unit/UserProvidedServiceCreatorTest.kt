package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.tools.pushapps.CloudFoundryClient
import org.cloudfoundry.tools.pushapps.config.UserProvidedServiceConfig
import org.cloudfoundry.tools.pushapps.UserProvidedServiceCreator
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserProvidedServiceCreatorTest : Spek({
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
            serviceConfigs = listOf(serviceConfig),
            maxInFlight = 1
        )

        return TestContext(serviceCreator, mockCloudFoundryClient, serviceConfig)
    }

    describe("#createOrUpdateServices") {
        it("it creates the services and returns a success result") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(emptyList()))
            whenever(tc.mockCloudFoundryClient.createUserProvidedService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createOrUpdateServices().toIterable().toList()
            verify(tc.mockCloudFoundryClient, times(1))
                .createUserProvidedService(tc.serviceConfig)

            assertThat(results).hasSize(1)

            val firstResult = results[0]
            assertThat(firstResult.description).isEqualTo("Creating user provided service ${tc.serviceConfig.name}")
            assertThat(firstResult.didSucceed).isTrue()
        }

        it("it returns a failure result when creating a service fails") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(emptyList()))
            whenever(tc.mockCloudFoundryClient.createUserProvidedService(any())).thenReturn(
                Mono.error(Exception("lemons"))
            )

            val results = tc.serviceCreator.createOrUpdateServices().toIterable().toList()
            verify(tc.mockCloudFoundryClient, times(1))
                .createUserProvidedService(tc.serviceConfig)

            assertThat(results).hasSize(1)
            val result = results[0]
            assertThat(result.didSucceed).isFalse()
            assertThat(result.description).isEqualTo("Create user provided service ${tc.serviceConfig.name}")
            assertThat(result.error!!.message).contains("lemons")
        }

        it("it updates services that already exist") {
            val tc = buildTestContext()
            whenever(tc.mockCloudFoundryClient.listServices()).thenReturn(Flux.fromIterable(listOf(tc.serviceConfig.name)))
            whenever(tc.mockCloudFoundryClient.updateUserProvidedService(any())).thenReturn(Mono.empty())

            val results = tc.serviceCreator.createOrUpdateServices().toIterable().toList()
            verify(tc.mockCloudFoundryClient, times(1))
                .updateUserProvidedService(tc.serviceConfig)

            assertThat(results).hasSize(1)

            val firstResult = results[0]
            assertThat(firstResult.description).isEqualTo("Creating user provided service ${tc.serviceConfig.name}")
            assertThat(firstResult.didSucceed).isTrue()
        }
    }
})

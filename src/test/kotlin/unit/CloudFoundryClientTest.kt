package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.Applications
import org.cloudfoundry.operations.organizations.OrganizationSummary
import org.cloudfoundry.operations.organizations.Organizations
import org.cloudfoundry.operations.routes.Routes
import org.cloudfoundry.operations.services.ServiceInstanceSummary
import org.cloudfoundry.operations.services.ServiceInstanceType
import org.cloudfoundry.operations.services.Services
import org.cloudfoundry.operations.spaces.SpaceSummary
import org.cloudfoundry.operations.spaces.Spaces
import pushapps.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class CloudFoundryClientTest : Test({
    data class TestContext(
        val cloudFoundryClient: CloudFoundryClient,
        val mockApplications: Applications,
        val mockServices: Services,
        val mockOrganizations: Organizations,
        val mockSpaces: Spaces,
        val mockRoutes: Routes,
        val mockCfOperations: CloudFoundryOperations
    )

    fun buildTestContext(): TestContext {
        val mockApplications = mock<Applications>()
        val mockServices = mock<Services>()
        val mockOrganizations = mock<Organizations>()
        val mockSpaces = mock<Spaces>()
        val mockRoutes = mock<Routes>()

        val mockCfOperations = mock<DefaultCloudFoundryOperations>()
        whenever(mockCfOperations.applications()).thenReturn(mockApplications)
        whenever(mockCfOperations.services()).thenReturn(mockServices)
        whenever(mockCfOperations.organizations()).thenReturn(mockOrganizations)
        whenever(mockCfOperations.spaces()).thenReturn(mockSpaces)
        whenever(mockCfOperations.routes()).thenReturn(mockRoutes)

        val cloudFoundryClient = CloudFoundryClient(
            apiHost = "api.host",
            username = "username",
            password = "password",
            cloudFoundryOperations = mockCfOperations)

        return TestContext(
            cloudFoundryClient = cloudFoundryClient,
            mockCfOperations = mockCfOperations,
            mockApplications = mockApplications,
            mockServices = mockServices,
            mockOrganizations = mockOrganizations,
            mockSpaces = mockSpaces,
            mockRoutes = mockRoutes
        )
    }

    describe("#createService") {
        test("creates the given service") {
            val tc = buildTestContext()
            whenever(tc.mockServices.createInstance(any())).thenReturn(Mono.empty())

            val serviceConfig = ServiceConfig(
                name = "some-service",
                plan = "some-plan",
                broker = "some-broker"
            )

            tc.cloudFoundryClient.createService(serviceConfig)

            verify(tc.mockServices, times(1)).createInstance(
                argForWhich {
                    serviceInstanceName == serviceConfig.name &&
                        planName == serviceConfig.plan &&
                        serviceName == serviceConfig.broker
                }
            )
        }
    }

    describe("#createUserProvidedService") {
        test("creates the given service") {
            val tc = buildTestContext()
            whenever(tc.mockServices.createUserProvidedInstance(any())).thenReturn(Mono.empty())

            val serviceConfig = UserProvidedServiceConfig(
                name = "Foo bar",
                credentials = mapOf(
                    "FOO" to "BAR",
                    "BAR" to "BAZ"
                )
            )

            tc.cloudFoundryClient.createUserProvidedService(serviceConfig)

            verify(tc.mockServices, times(1)).createUserProvidedInstance(
                argForWhich {
                    name == serviceConfig.name &&
                        credentials == serviceConfig.credentials
                }
            )
        }
    }

    describe("#updateUserProvidedService") {
        test("updates the given service") {
            val tc = buildTestContext()
            whenever(tc.mockServices.updateUserProvidedInstance(any())).thenReturn(Mono.empty())

            val serviceConfig = UserProvidedServiceConfig(
                name = "Foo bar",
                credentials = mapOf(
                    "FOO" to "BAR",
                    "BAR" to "BAZ"
                )
            )

            tc.cloudFoundryClient.updateUserProvidedService(serviceConfig)

            verify(tc.mockServices, times(1)).updateUserProvidedInstance(
                argForWhich {
                    userProvidedServiceInstanceName == serviceConfig.name &&
                        credentials == serviceConfig.credentials
                }
            )
        }
    }

    describe("#pushApplication") {
        //TODO
    }

    describe("#startApplication") {
        val tc = buildTestContext()
        whenever(tc.mockApplications.start(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.startApplication("some-app")

        verify(tc.mockApplications, times(1)).start(
            argForWhich {
                name == "some-app"
            }
        )
    }

    describe("#stopApplication") {
        val tc = buildTestContext()
        whenever(tc.mockApplications.stop(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.stopApplication("some-app")

        verify(tc.mockApplications, times(1)).stop(
            argForWhich {
                name == "some-app"
            }
        )
    }

    describe("#setApplicationEnvironment") {
        test("sets environment variables if present in config") {
            val tc = buildTestContext()
            whenever(tc.mockApplications.setEnvironmentVariable(any())).thenReturn(Mono.empty())

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
            val tc = buildTestContext()
            val appConfig = AppConfig(
                name = "Foo bar",
                path = "/tmp/foo/bar",
                environment = mapOf()
            )

            tc.cloudFoundryClient.setApplicationEnvironment(appConfig)

            verify(tc.mockApplications, times(0)).setEnvironmentVariable(any())
        }
    }

    describe("#bindServicesToApplication") {
        test("binds each service to the app") {

            val tc = buildTestContext()

            tc.cloudFoundryClient.bindServicesToApplication("some-app", listOf("some-service", "some-other-service"))

            verify(tc.mockServices, times(1)).bind(argForWhich {
                applicationName == "some-app" &&
                    serviceInstanceName == "some-service"
            })
            verify(tc.mockServices, times(1)).bind(argForWhich {
                applicationName == "some-app" &&
                    serviceInstanceName == "some-other-service"
            })
        }
    }

    describe("#mapRoute") {
        val tc = buildTestContext()
        val appConfig = AppConfig(
            name = "Foo bar",
            path = "/tmp/foo/bar",
            environment = mapOf(),
            domain = "tree",
            route = Route(
                hostname = "lemons",
                path = "citrus"
            )
        )

        whenever(tc.mockRoutes.map(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.mapRoute(appConfig)

        verify(tc.mockRoutes, times(1)).map(
            argForWhich {
                applicationName == appConfig.name &&
                    domain == appConfig.domain &&
                    host == appConfig.route!!.hostname &&
                    path == appConfig.route!!.path
            }
        )
    }

    describe("#unmapRoute") {
        val tc = buildTestContext()
        val appConfig = AppConfig(
            name = "Foo bar",
            path = "/tmp/foo/bar",
            environment = mapOf(),
            domain = "tree",
            route = Route(
                hostname = "lemons",
                path = "citrus"
            )
        )

        whenever(tc.mockRoutes.unmap(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.unmapRoute(appConfig)

        verify(tc.mockRoutes, times(1)).unmap(
            argForWhich {
                applicationName == appConfig.name &&
                    domain == appConfig.domain &&
                    host == appConfig.route!!.hostname &&
                    path == appConfig.route!!.path
            }
        )
    }

    describe("#createAndTargetOrganization") {
        //TODO
    }

    describe("#createAndTargetSpace") {
        //TODO
    }

    describe("#listOrganizations") {
        val tc = buildTestContext()
        val orgSummary = OrganizationSummary.builder()
            .name("some-name")
            .id("some-id")
            .build()

        whenever(tc.mockOrganizations.list()).thenReturn(
            Flux.fromArray(arrayOf(orgSummary)))

        val orgResults = tc.cloudFoundryClient.listOrganizations()
        assertThat(orgResults).isEqualTo(listOf("some-name"))
    }

    describe("#listSpaces") {
        val tc = buildTestContext()
        val spaceSummary = SpaceSummary.builder()
            .name("some-name")
            .id("some-id")
            .build()

        whenever(tc.mockSpaces.list()).thenReturn(
            Flux.fromArray(arrayOf(spaceSummary)))

        val spaceResults = tc.cloudFoundryClient.listSpaces()
        assertThat(spaceResults).isEqualTo(listOf("some-name"))
    }

    describe("#listServices") {
        val tc = buildTestContext()
        val serviceSummary = ServiceInstanceSummary.builder()
            .name("some-name")
            .id("some-id")
            .type(ServiceInstanceType.MANAGED)
            .build()

        whenever(tc.mockServices.listInstances()).thenReturn(
            Flux.fromArray(arrayOf(serviceSummary)))

        val serviceResults = tc.cloudFoundryClient.listServices()
        assertThat(serviceResults).isEqualTo(listOf("some-name"))
    }
})

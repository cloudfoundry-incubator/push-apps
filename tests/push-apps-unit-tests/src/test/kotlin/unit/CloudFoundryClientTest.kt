package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.client.v2.securitygroups.Protocol
import org.cloudfoundry.client.v2.securitygroups.SecurityGroups
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.Applications
import org.cloudfoundry.operations.organizations.OrganizationSummary
import org.cloudfoundry.operations.organizations.Organizations
import org.cloudfoundry.operations.routes.Routes
import org.cloudfoundry.operations.services.ServiceInstanceSummary
import org.cloudfoundry.operations.services.ServiceInstanceType
import org.cloudfoundry.operations.services.Services
import org.cloudfoundry.operations.spaces.SpaceDetail
import org.cloudfoundry.operations.spaces.SpaceSummary
import org.cloudfoundry.operations.spaces.Spaces
import org.cloudfoundry.tools.pushapps.*
import org.cloudfoundry.tools.pushapps.config.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class CloudFoundryClientTest : Spek({
    data class TestContext(
        val cloudFoundryClient: CloudFoundryClient,
        val applications: Applications,
        val services: Services,
        val organizations: Organizations,
        val spaces: Spaces,
        val routes: Routes,
        val cfOperations: CloudFoundryOperations,
        val cfOperationsBuilder: CloudFoundryOperationsBuilder,
        val securityGroups: SecurityGroups
    )

    fun buildTestContext(): TestContext {
        val mockApplications = mock<Applications>()
        val mockServices = mock<Services>()
        val mockOrganizations = mock<Organizations>()
        val mockSpaces = mock<Spaces>()
        val mockRoutes = mock<Routes>()
        val mockSecurityGroups = mock<SecurityGroups>()

        val mockCfClient = mock<org.cloudfoundry.client.CloudFoundryClient>()
        val mockCfOperations = mock<DefaultCloudFoundryOperations>()
        val mockCfOperationsBuilder = mock<CloudFoundryOperationsBuilder>()

        whenever(mockCfOperations.applications()).thenReturn(mockApplications)
        whenever(mockCfOperations.services()).thenReturn(mockServices)
        whenever(mockCfOperations.organizations()).thenReturn(mockOrganizations)
        whenever(mockCfOperations.spaces()).thenReturn(mockSpaces)
        whenever(mockCfOperations.routes()).thenReturn(mockRoutes)
        whenever(mockCfOperations.cloudFoundryClient).thenReturn(mockCfClient)

        whenever(mockCfClient.securityGroups()).thenReturn(mockSecurityGroups)

        whenever(mockServices.bind(any())).thenReturn(Mono.empty<Void>())

        val cloudFoundryClient = CloudFoundryClient(
            cloudFoundryOperations = mockCfOperations,
            cloudFoundryOperationsBuilder = mockCfOperationsBuilder,
            operationTimeoutInMinutes = 5L,
            retryCount = 1
        )

        return TestContext(
            cloudFoundryClient = cloudFoundryClient,
            cfOperations = mockCfOperations,
            cfOperationsBuilder = mockCfOperationsBuilder,
            applications = mockApplications,
            services = mockServices,
            organizations = mockOrganizations,
            spaces = mockSpaces,
            routes = mockRoutes,
            securityGroups = mockSecurityGroups
        )
    }

    describe("#createService") {
        it("creates the given service") {
            val tc = buildTestContext()
            whenever(tc.services.createInstance(any())).thenReturn(Mono.empty())

            val serviceConfig = ServiceConfig(
                    name = "some-service",
                    plan = "some-plan",
                    broker = "some-broker"
            )

            tc.cloudFoundryClient.createService(serviceConfig)

            verify(tc.services, times(1)).createInstance(
                argForWhich {
                    serviceInstanceName == serviceConfig.name &&
                        planName == serviceConfig.plan &&
                        serviceName == serviceConfig.broker
                }
            )
        }
    }

    describe("#createUserProvidedService") {
        it("creates the given service") {
            val tc = buildTestContext()
            whenever(tc.services.createUserProvidedInstance(any())).thenReturn(Mono.empty())

            val serviceConfig = UserProvidedServiceConfig(
                    name = "Foo bar",
                    credentials = mapOf(
                            "FOO" to "BAR",
                            "BAR" to "BAZ"
                    )
            )

            tc.cloudFoundryClient.createUserProvidedService(serviceConfig)

            verify(tc.services, times(1)).createUserProvidedInstance(
                argForWhich {
                    name == serviceConfig.name &&
                        credentials == serviceConfig.credentials
                }
            )
        }
    }

    describe("#updateUserProvidedService") {
        it("updates the given service") {
            val tc = buildTestContext()
            whenever(tc.services.updateUserProvidedInstance(any())).thenReturn(Mono.empty())

            val serviceConfig = UserProvidedServiceConfig(
                    name = "Foo bar",
                    credentials = mapOf(
                            "FOO" to "BAR",
                            "BAR" to "BAZ"
                    )
            )

            tc.cloudFoundryClient.updateUserProvidedService(serviceConfig)

            verify(tc.services, times(1)).updateUserProvidedInstance(
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
        whenever(tc.applications.start(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.startApplication("some-app")

        verify(tc.applications, times(1)).start(
            argForWhich {
                name == "some-app"
            }
        )
    }

    describe("#stopApplication") {
        val tc = buildTestContext()
        whenever(tc.applications.stop(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.stopApplication("some-app")

        verify(tc.applications, times(1)).stop(
            argForWhich {
                name == "some-app"
            }
        )
    }

    describe("#setApplicationEnvironment") {
        it("sets environment variables if present in config") {
            val tc = buildTestContext()
            whenever(tc.applications.setEnvironmentVariable(any())).thenReturn(Mono.empty())

            val appConfig = AppConfig(
                    name = "Foo bar",
                    path = "/tmp/foo/bar",
                    environment = mapOf(
                            "FOO" to "BAR",
                            "BAR" to "BAZ"
                    )
            )

            tc.cloudFoundryClient.setApplicationEnvironment(appConfig).block()

            verify(tc.applications, times(1)).setEnvironmentVariable(
                argForWhich {
                    name == appConfig.name &&
                        variableName == "FOO" &&
                        variableValue == "BAR"
                }
            )

            verify(tc.applications, times(1)).setEnvironmentVariable(
                argForWhich {
                    name == appConfig.name &&
                        variableName == "BAR" &&
                        variableValue == "BAZ"
                }
            )
        }

        it("does not set environment variables if none in config") {
            val tc = buildTestContext()
            val appConfig = AppConfig(
                    name = "Foo bar",
                    path = "/tmp/foo/bar",
                    environment = mapOf()
            )

            tc.cloudFoundryClient.setApplicationEnvironment(appConfig)

            verify(tc.applications, times(0)).setEnvironmentVariable(any())
        }
    }

    describe("#bindServicesToApplication") {
        it("binds each service to the app") {
            val tc = buildTestContext()

            tc.cloudFoundryClient.bindServicesToApplication("some-app", listOf("some-service", "some-other-service"))

            verify(tc.services, times(1)).bind(argForWhich {
                applicationName == "some-app" &&
                    serviceInstanceName == "some-service"
            })
            verify(tc.services, times(1)).bind(argForWhich {
                applicationName == "some-app" &&
                    serviceInstanceName == "some-other-service"
            })
        }
    }

    describe("#mapRoute") {
        it("it maps the routes") {
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

            whenever(tc.routes.map(any())).thenReturn(Mono.empty())

            tc.cloudFoundryClient.mapRoute(appConfig)

            verify(tc.routes, times(1)).map(
                argForWhich {
                    applicationName == appConfig.name &&
                        domain == appConfig.domain &&
                        host == appConfig.route!!.hostname &&
                        path == appConfig.route!!.path
                }
            )
        }
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

        whenever(tc.routes.unmap(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.unmapRoute(appConfig)

        verify(tc.routes, times(1)).unmap(
            argForWhich {
                applicationName == appConfig.name &&
                    domain == appConfig.domain &&
                    host == appConfig.route!!.hostname &&
                    path == appConfig.route!!.path
            }
        )
    }

    describe("#createSecurityGroup") {
        val tc = buildTestContext()
        val securityConfig = SecurityGroup(
                name = "Foo bar",
                destination = "destination somewhere",
                protocol = "all"
        )

        whenever(tc.securityGroups.create(any())).thenReturn(Mono.empty())

        tc.cloudFoundryClient.createSecurityGroup(securityConfig, "some-space")

        verify(tc.securityGroups, times(1)).create(
            argForWhich {
                spaceIds.size == 1 &&
                    spaceIds[0] == "some-space" &&
                    rules.size == 1 &&
                    rules[0].destination == "destination somewhere" &&
                    rules[0].protocol == Protocol.from("all")
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

        whenever(tc.organizations.list()).thenReturn(
            Flux.fromArray(arrayOf(orgSummary)))

        val orgResults = tc.cloudFoundryClient
            .listOrganizations()
            .toIterable()
            .toList()
        assertThat(orgResults).isEqualTo(listOf("some-name"))
    }

    describe("#listSpaces") {
        val tc = buildTestContext()
        val spaceSummary = SpaceSummary.builder()
            .name("some-name")
            .id("some-id")
            .build()

        whenever(tc.spaces.list()).thenReturn(
            Flux.fromArray(arrayOf(spaceSummary)))

        val spaceResults = tc.cloudFoundryClient
            .listSpaces()
            .toIterable()
            .toList()
        assertThat(spaceResults).isEqualTo(listOf("some-name"))
    }

    describe("#listServices") {
        val tc = buildTestContext()
        val serviceSummary = ServiceInstanceSummary.builder()
            .name("some-name")
            .id("some-id")
            .type(ServiceInstanceType.MANAGED)
            .build()

        whenever(tc.services.listInstances()).thenReturn(
            Flux.fromArray(arrayOf(serviceSummary)))

        val serviceResults = tc.cloudFoundryClient
            .listServices()
            .toIterable()
            .toList()
        assertThat(serviceResults).isEqualTo(listOf("some-name"))
    }

    describe("#getSpace") {
        it("it uses cloud foundry operations to get the space") {
            val tc = buildTestContext()

            val spaceDetail = SpaceDetail
                .builder()
                .id("pamplemousse")
                .name("")
                .organization("")
                .build()

            whenever(tc.spaces.get(any())).thenReturn(Mono.just(spaceDetail))

            val spaceId = tc.cloudFoundryClient.getSpaceId("some-space").block()
            verify(tc.spaces, times(1)).get(
                argForWhich {
                    name == "some-space"
                }
            )
            assertThat(spaceId).isEqualTo("pamplemousse")
        }
    }

    describe("#fetchRecentLogsForAsync") {
        it("uses cloud found operations to fetch recent logs") {
            val tc = buildTestContext()
            val logMessage = mock<LogMessage>()


            whenever(tc.applications.logs(any())).thenReturn(Flux.fromIterable(listOf(logMessage)))

            val logs = tc.cloudFoundryClient.fetchRecentLogsForAsync("some-app")
            verify(tc.applications, times(1)).logs(
                argForWhich {
                    name == "some-app" &&
                        recent == true
                }
            )
            val logsList = logs.toIterable().toList()
            assertThat(logsList).hasSize(1)
            assertThat(logsList[0]).isEqualTo(logMessage)
        }
    }
})

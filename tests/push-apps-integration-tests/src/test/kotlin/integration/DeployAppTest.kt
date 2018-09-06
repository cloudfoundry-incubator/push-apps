package integration

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.routes.MapRouteRequest
import org.cloudfoundry.operations.routes.UnmapRouteRequest
import org.cloudfoundry.operations.stacks.Stack
import org.cloudfoundry.tools.pushapps.*
import org.cloudfoundry.tools.pushapps.config.AppConfig
import org.cloudfoundry.tools.pushapps.config.Route
import org.cloudfoundry.tools.pushapps.config.ServiceConfig
import org.cloudfoundry.tools.pushapps.config.UserProvidedServiceConfig
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Paths

class DeployAppTest : Spek({
    describe("pushApps interacts with applications by") {
        val helloApp = AppConfig(
                name = "hello",
                path = "$workingDir/src/test/kotlin/support/helloapp.zip",
                buildpack = "binary_buildpack",
                command = "./helloapp",
                stackPriority = listOf(
                        "stack1",
                        "stack2",
                        "stack3"
                ),
                environment = mapOf(
                        "NAME" to "Steve"
                )
        )

        val goodbyeApp = AppConfig(
                name = "goodbye",
                path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
                buildpack = "binary_buildpack",
                command = "./goodbyeapp",
                noRoute = true,
                domain = "example.com",
                route = Route(
                        hostname = "oranges",
                        path = "/v1"
                ),
                environment = mapOf(
                        "NAME" to "George"
                )
        )

        val blueGreenApp = AppConfig(
                name = "generic",
                path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
                buildpack = "binary_buildpack",
                command = "./goodbyeapp",
                environment = mapOf(
                        "NAME" to "BLUE OR GREEN"
                ),
                noRoute = true,
                domain = "example.com",
                route = Route(
                        hostname = "generic"
                ),
                blueGreenDeploy = true
        )

        it("pushing every application in the config file") {
            val tc = buildTestContext(
                apps = listOf(helloApp, goodbyeApp), organization = "foo_bar_org"
            )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.applications()).push(
                argForWhich {
                    path == Paths.get(helloApp.path) &&
                        name == helloApp.name &&
                        buildpack == helloApp.buildpack &&
                        command == helloApp.command
                })

            verify(tc.cfOperations.applications()).push(
                argForWhich {
                    path == Paths.get(goodbyeApp.path) &&
                        name == goodbyeApp.name &&
                        buildpack == goodbyeApp.buildpack &&
                        command == goodbyeApp.command
                })
        }

        it("uses first available stack in stack priority list") {
            val tc = buildTestContext(
                    apps = listOf(helloApp), organization = "foo_bar_org"
            )

            val pushApps = PushApps(
                    tc.config,
                    tc.cfClientBuilder
            )

            whenever(tc.cfOperations.stacks().list())
                    .thenReturn(Flux.just(
                            Stack.builder().name("stack2").description("").id("").build(),
                            Stack.builder().name("stack3").description("").id("").build()
                    ))

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.applications()).push(
                    argForWhich {
                        stack == "stack2"
                    })
        }

        it("setting the env vars on the app") {
            val appsWithEnvs = AppConfig(
                    name = "best-app",
                    path = "omfgdogs.com",
                    buildpack = "binary_buildpack",
                    command = "./doges",
                    environment = mapOf(
                            "DISNEY" to "Zootopia",
                            "PIXAR" to "Finding Dory",
                            "DREAMWORKS" to "How to Train Your Dragon"
                    ),
                    serviceNames = listOf(
                            "compliment-service",
                            "my-mf-service",
                            "optional-service"
                    )
            )

            val tc = buildTestContext(
                apps = listOf(appsWithEnvs), organization = "foo_bar_org"
            )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.applications()).setEnvironmentVariable(
                argForWhich {
                    name == appsWithEnvs.name
                    variableName == "DISNEY"
                    variableValue == "Zootopia"
                })

            verify(tc.cfOperations.applications()).setEnvironmentVariable(
                argForWhich {
                    name == appsWithEnvs.name
                    variableName == "PIXAR"
                    variableValue == "Finding Dory"
                })

            verify(tc.cfOperations.applications()).setEnvironmentVariable(
                argForWhich {
                    name == appsWithEnvs.name
                    variableName == "DREAMWORKS"
                    variableValue == "How to Train Your Dragon"
                })
        }

        it("creating and binding requested services") {
            val dogService = UserProvidedServiceConfig(
                    name = "dog-walking-service",
                    credentials = mapOf("alan" to "walker")
            )

            val catService = ServiceConfig(
                    name = "my-cat-service",
                    plan = "scratches",
                    broker = "cat-service",
                    optional = true
            )

            val appsWithServices = AppConfig(
                    name = "best-app",
                    path = "omfgdogs.com",
                    buildpack = "binary_buildpack",
                    command = "./doges",
                    serviceNames = listOf(
                            "my-cat-service",
                            "dog-walking-service"
                    )
            )

            val tc = buildTestContext(
                apps = listOf(appsWithServices),
                services = listOf(catService),
                userProvidedServices = listOf(dogService), organization = "foo_bar_org"
            )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.services()).createUserProvidedInstance(
                argForWhich {
                    name == "dog-walking-service"
                    credentials == mapOf("alan" to "walker")
                })

            verify(tc.cfOperations.services()).createInstance(
                argForWhich {
                    serviceName == "cat-service"
                    serviceInstanceName == "my-cat-service"
                    planName == "scratches"
                })

            verify(tc.cfOperations.services()).createInstance(
                argForWhich {
                    serviceName == "cat-service"
                    serviceInstanceName == "my-cat-service"
                    planName == "scratches"
                })

            verify(tc.cfOperations.services()).bind(
                argForWhich {
                    applicationName == appsWithServices.name
                    serviceInstanceName == "dog-walking-service"
                })

            verify(tc.cfOperations.services()).bind(
                argForWhich {
                    applicationName == appsWithServices.name
                    serviceInstanceName == "my-cat-service"
                })
        }

        it("blue green deploying applications with blue green set to true") {
            val greenAppSummary = mock<ApplicationSummary>()
            whenever(greenAppSummary.name).thenReturn(blueGreenApp.name)

            val tc = buildTestContext(
                apps = listOf(blueGreenApp), organization = "foo_bar_org"
            )

            whenever(tc.cfOperations.applications().list())
                .thenReturn(Flux.fromIterable(listOf(greenAppSummary)))

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()
            assertThat(result).isTrue()

            verify(tc.cfOperations.applications()).push(
                argForWhich {
                    name == "${blueGreenApp.name}-blue"
                })

            verify(tc.cfOperations.applications()).push(
                argForWhich {
                    name == blueGreenApp.name
                })


            argumentCaptor<UnmapRouteRequest>().apply {
                verify(tc.cfOperations.routes(), times(2)).unmap(capture())

                assertThat(allValues.size).isEqualTo(2)

                val unmapGreenAppReq = allValues.find { it.applicationName == blueGreenApp.name }!!
                assertThat(unmapGreenAppReq.domain).isEqualTo(blueGreenApp.domain)
                assertThat(unmapGreenAppReq.host).isEqualTo(blueGreenApp.route!!.hostname)

                val unmapBlueAppReq = allValues.find { it.applicationName == "${blueGreenApp.name}-blue" }!!
                assertThat(unmapBlueAppReq.domain).isEqualTo(blueGreenApp.domain)
                assertThat(unmapBlueAppReq.host).isEqualTo(blueGreenApp.route!!.hostname)
            }

            argumentCaptor<MapRouteRequest>().apply {
                verify(tc.cfOperations.routes(), times(2)).map(capture())

                assertThat(allValues.size).isEqualTo(2)

                val unmapGreenAppReq = allValues.find { it.applicationName == blueGreenApp.name }!!
                assertThat(unmapGreenAppReq.domain).isEqualTo(blueGreenApp.domain)
                assertThat(unmapGreenAppReq.host).isEqualTo(blueGreenApp.route!!.hostname)

                val unmapBlueAppReq = allValues.find { it.applicationName == "${blueGreenApp.name}-blue" }!!
                assertThat(unmapBlueAppReq.domain).isEqualTo(blueGreenApp.domain)
                assertThat(unmapBlueAppReq.host).isEqualTo(blueGreenApp.route!!.hostname)
            }
        }

        it("retries failed deployments up to the retry count") {
            val tc = buildTestContext(
                apps = listOf(helloApp),
                retryCount = 3
            )

            whenever(
                tc.cfOperations.applications().push(any())
            ).thenAnswer {
                val error = RuntimeException("it broke")
                Mono.error<Void>(error)
            }

            val mockLogMessage = mock<LogMessage>()
            whenever(tc.cfOperations.applications().logs(any()))
                .thenReturn(Flux.fromIterable(listOf(mockLogMessage)))

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()
            assertThat(result).isFalse()

            verify(tc.cfOperations.applications(), times(4)).push(argForWhich {
                name == helloApp.name
            })
        }

        it("retries failed deployments correctly when the number of deployments is higher than maxInFlight count") {
            //max in flight of 2, deploy 3 apps, have 2nd app fail 2 times
            val blueGreenGoodbye1 = AppConfig(
                    name = "goodbye",
                    path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
                    buildpack = "binary_buildpack",
                    command = "./goodbyeapp",
                    environment = mapOf(
                            "NAME" to "BLUE OR GREEN",
                            "TRUTH" to "OUT THERE"
                    ),
                    noRoute = true,
                    domain = "example.com",
                    route = Route(
                            hostname = "generic"
                    ),
                    blueGreenDeploy = true
            )

            val blueGreenGoodbye2 = AppConfig(
                    name = "shalom",
                    path = "$workingDir/src/test/kotlin/support/goodbyeapp.zip",
                    buildpack = "binary_buildpack",
                    command = "./goodbyeapp",
                    environment = mapOf(
                            "NAME" to "BLUE OR GREEN",
                            "HELLO" to "WORLD"
                    ),
                    noRoute = true,
                    domain = "example.com",
                    route = Route(
                            hostname = "generic"
                    ),
                    blueGreenDeploy = true
            )

            val blueGreenHello = AppConfig(
                    name = "hello",
                    path = "$workingDir/src/test/kotlin/support/helloapp.zip",
                    buildpack = "binary_buildpack",
                    command = "./helloapp",
                    environment = mapOf(
                            "NAME" to "BLUE OR GREEN",
                            "RED_FISH" to "BLUE_FISH"
                    ),
                    noRoute = true,
                    domain = "example.com",
                    route = Route(
                            hostname = "generic"
                    ),
                    blueGreenDeploy = true
            )

            val tc = buildTestContext(
                apps = listOf(blueGreenGoodbye1, blueGreenGoodbye2, blueGreenHello),
                organization = "foo_bar_org",
                maxInFlight = 2,
                retryCount = 3,
                cfOperationTimeoutInMinutes = 1L
            )

            whenever(tc.cfOperations.applications().start(argForWhich {
                name == "shalom"
            })).thenReturn(
                Mono.error<Void>(PushAppsError("it broke")),
                Mono.error<Void>(PushAppsError("it broke")),
                Mono.empty<Void>()
            )

            whenever(tc.cfOperations.applications().start(argForWhich {
                name == "goodbye" ||
                    name == "hello" ||
                    name == "goodbye-blue" ||
                    name == "hello-blue" ||
                    name == "shalom-blue"
            })).thenReturn(
                Mono.empty<Void>()
            )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )
            pushApps.pushApps()

            verify(tc.cfOperations.applications(), times(4)).push(argForWhich {
                name == "hello" ||
                    name == "hello-blue" ||
                    name == "goodbye" ||
                    name == "goodbye-blue"

            })

            verify(tc.cfOperations.applications(), times(4)).start(argForWhich {
                name == "hello" ||
                    name == "hello-blue" ||
                    name == "goodbye" ||
                    name == "goodbye-blue"
            })

            //FIXME: we should not retry the green if the blue fails, and vice-versa
            verify(tc.cfOperations.applications(), times(1)).push(argForWhich {
                name == "shalom-blue"

            })
            verify(tc.cfOperations.applications(), times(1)).push(argForWhich {
                name == "shalom"
            })

            verify(tc.cfOperations.applications(), times(1)).start(argForWhich {
                name == "shalom-blue"
            })

            verify(tc.cfOperations.applications(), times(3)).start(argForWhich {
                name == "shalom"
            })
        }

        it("returning an error if a deploy fails and fetches logs for failed operation") {
            val tc = buildTestContext(
                apps = listOf(helloApp), organization = "foo_bar_org", retryCount = 0
            )

            whenever(
                tc.cfOperations.applications().push(any())
            ).thenAnswer {
                val error = RuntimeException("it broke")
                Mono.error<Void>(error)
            }

            val mockLogMessage = mock<LogMessage>()
            whenever(tc.cfOperations.applications().logs(any()))
                .thenReturn(Flux.fromIterable(listOf(mockLogMessage)))

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()
            assertThat(result).isFalse()

            verify(tc.cfOperations.applications()).logs(argForWhich {
                name == helloApp.name
            })
        }
    }
})

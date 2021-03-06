package unit

import com.nhaarman.mockito_kotlin.*
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationHealthCheck
import org.cloudfoundry.operations.applications.Applications
import org.cloudfoundry.tools.pushapps.config.AppConfig
import org.cloudfoundry.tools.pushapps.PushApplication
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


class PushApplicationTest : Spek({
    data class TestContext(
        val pushApplication: PushApplication,
        val mockApplications: Applications
    )

    fun buildTestContext(appConfig: AppConfig, availableStacks: Flux<String> = Flux.empty()): TestContext {
        val mockApplications = mock<Applications>()

        whenever(mockApplications.push(any())).thenReturn(Mono.empty())

        val mockCfOperations = mock<CloudFoundryOperations>()
        whenever(mockCfOperations.applications()).thenReturn(mockApplications)

        val pushApplication = PushApplication(
                cloudFoundryOperations = mockCfOperations,
                appConfig = appConfig,
                availableStacksFlux = availableStacks
        )

        return TestContext(pushApplication, mockApplications)
    }

    describe("#generatePushAppAction") {
        //TODO test other things than optional config vars

        describe("optional app config variables") {
            it("sets buildpack variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        buildpack = "bob_the_builder"
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { buildpack == "bob_the_builder" }
                )
            }

            it("sets command variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        command = "some-command"
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { command == "some-command" }
                )
            }

            it("sets instances variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        instances = 1
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { instances == 1 }
                )
            }

            it("sets disk quota variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        diskQuota = 512
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { diskQuota == 512 }
                )
            }

            it("sets memory variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        memory = 512
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { memory == 512 }
                )
            }

            it("sets noHostname variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        noHostname = true
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { noHostname == true }
                )
            }

            it("sets noRoute variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        noRoute = true
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { noRoute == true }
                )
            }

            it("sets timeout variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        timeout = 100
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { timeout == 100 }
                )
            }

            it("sets domain variable if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        domain = "lemons"
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { domain == "lemons" }
                )
            }

            it("sets healthcheck type if present in config") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        healthCheckType = "none"
                )
                val tc = buildTestContext(appConfig)

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { healthCheckType == ApplicationHealthCheck.NONE }
                )
            }

            it("sets stack to first available stack in stack priority list") {
                val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        stackPriority = listOf("first-priority-stack", "second-priority-stack", "third-priority-stack")
                )
                val tc = buildTestContext(appConfig, Flux.just("second-priority-stack", "third-priority-stack"))

                tc.pushApplication.generatePushAppAction()

                verify(tc.mockApplications).push(
                    argForWhich { stack == "second-priority-stack" }
                )
            }
        }
    }
})

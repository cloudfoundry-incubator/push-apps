package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationHealthCheck
import org.cloudfoundry.operations.applications.Applications
import pushapps.AppConfig
import pushapps.PushApplication
import reactor.core.publisher.Mono



class PushApplicationTest : Test({
    data class TestContext(
        val pushApplication: PushApplication,
        val mockApplications: Applications
    )

    fun buildTestContext(appConfig: AppConfig): TestContext {
        val mockApplications = mock<Applications>()
        whenever(mockApplications.push(any())).thenReturn(Mono.empty())

        val mockCfOperations = mock<CloudFoundryOperations>()
        whenever(mockCfOperations.applications()).thenReturn(mockApplications)

        val pushApplication = PushApplication(
            cloudFoundryOperations = mockCfOperations,
            appConfig = appConfig
        )

        return TestContext(pushApplication, mockApplications)
    }

    describe("#generatePushAppAction") {
        //TODO test other things than optional config vars

        describe("optional app config variables") {
            test("sets buildpack variable if present in config") {
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

            test("sets command variable if present in config") {
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

            test("sets instances variable if present in config") {
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

            test("sets disk quota variable if present in config") {
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

            test("sets memory variable if present in config") {
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

            test("sets noHostname variable if present in config") {
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

            test("sets noRoute variable if present in config") {
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

            test("sets timeout variable if present in config") {
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

            test("sets domain variable if present in config") {
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

            test("sets healthcheck type if present in config") {
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
        }
    }
})
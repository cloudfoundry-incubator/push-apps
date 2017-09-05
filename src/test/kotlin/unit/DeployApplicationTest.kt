package unit

import com.nhaarman.mockito_kotlin.*
import io.damo.aspen.Test
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationHealthCheck
import org.cloudfoundry.operations.applications.Applications
import pushapps.AppConfig
import pushapps.DeployApplication
import reactor.core.publisher.Mono

data class TestContext(
    val cfOperations: CloudFoundryOperations,
    val cfApplicationOperations: Applications
)

fun buildTestContext(): TestContext {
    val cfOperations = mock<CloudFoundryOperations>()
    val cfApplicationOperations = mock<Applications>()

    whenever(cfOperations.applications()).thenReturn(cfApplicationOperations)
    whenever(cfApplicationOperations.push(any())).thenReturn(Mono.empty())
    whenever(cfApplicationOperations.setEnvironmentVariable(any())).thenReturn(Mono.empty())

    return TestContext(cfOperations, cfApplicationOperations)
}

fun deployApplication(tc: TestContext, appConfig: AppConfig) {
    val deployApplication = DeployApplication(
        cloudFoundryOperations = tc.cfOperations,
        appConfig = appConfig
    )

    deployApplication.deploy()
}

class DeployApplicationTest : Test({
    describe("DeployApplication") {
        describe("#deploy") {
            describe("environment variables") {
                test("sets environment variables if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        environment = mapOf(
                            "FOO" to "BAR",
                            "BAR" to "BAZ"
                        ), instances = 1, diskQuota = 512, memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).setEnvironmentVariable(
                        argForWhich { variableName == "FOO" && variableValue == "BAR" }
                    )
                    verify(tc.cfApplicationOperations).setEnvironmentVariable(
                        argForWhich { variableName == "BAR" && variableValue == "BAZ" }
                    )
                }

                test("does not set environment variables if none in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar", instances = 1, diskQuota = 512, memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations, times(0)).setEnvironmentVariable(any())
                }
            }

            describe("optional app config variables") {
                test("sets buildpack variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        buildpack = "bob_the_builder", instances = 1, diskQuota = 512, memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { buildpack == "bob_the_builder" }
                    )
                }

                test("sets command variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        command = "some-command", instances = 1, diskQuota = 512, memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { command == "some-command" }
                    )
                }

                test("sets instances variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        instances = 1, diskQuota = 512, memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { instances == 1 }
                    )
                }

                test("sets disk quota variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        diskQuota = 512, memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { diskQuota == 512 }
                    )
                }

                test("sets memory variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        memory = 512, noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { memory == 512 }
                    )
                }

                test("sets noHostname variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        noHostname = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { noHostname == true }
                    )
                }

                test("sets noRoute variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        noRoute = true
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { noRoute == true }
                    )
                }

                test("sets timeout variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        timeout = 100
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { timeout == 100 }
                    )
                }

                test("sets domain variable if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        domain = "lemons"
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { domain == "lemons" }
                    )
                }

                test("sets healthcheck type if present in config") {
                    val tc = buildTestContext()
                    val appConfig = AppConfig(
                        name = "Foo bar",
                        path = "/tmp/foo/bar",
                        healthCheckType = "none"
                    )

                    deployApplication(tc, appConfig)

                    verify(tc.cfApplicationOperations).push(
                        argForWhich { healthCheckType == ApplicationHealthCheck.NONE }
                    )
                }
            }
        }
    }
})
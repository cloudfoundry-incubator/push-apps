package unit

import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import pushapps.ConfigReader

class ConfigReaderTest: Test({
    describe("#parseConfig") {
        test("Parses the pushApps config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")
            assertThat(pushAppsConfig.pushApps.appDeployRetryCount).isEqualTo(3)
        }

        test("Parses the cf config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")

            assertThat(pushAppsConfig.cf.apiHost).isEqualTo("api.example.com")
            assertThat(pushAppsConfig.cf.username).isEqualTo("some-username")
            assertThat(pushAppsConfig.cf.password).isEqualTo("some-password")
            assertThat(pushAppsConfig.cf.organization).isEqualTo("some-organization")
            assertThat(pushAppsConfig.cf.space).isEqualTo("some-space")
            assertThat(pushAppsConfig.cf.skipSslValidation).isTrue()
            assertThat(pushAppsConfig.cf.dialTimeoutInMillis).isEqualTo(1000)
        }

        test("Parses the apps config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")

            val apps = pushAppsConfig.apps
            assertThat(apps).hasSize(1)

            val app1 = apps[0]
            assertThat(app1.name).isEqualTo("some-name")
            assertThat(app1.path).isEqualTo("some-path")
            assertThat(app1.buildpack).isEqualTo("some-buildpack")

            assertThat(app1.environment).isEqualTo(mapOf("FRUIT" to "lemons"))
            assertThat(app1.serviceNames).isEqualTo(listOf("some-service-name"))

            assertThat(app1.route!!.hostname).isEqualTo("lemons")
            assertThat(app1.route!!.path).isEqualTo("/citrus")
        }

        test("Parses the services config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")

            val services = pushAppsConfig.services
            assertThat(services).hasSize(1)

            val service = services!![0]
            assertThat(service.name).isEqualTo("some-service-name")
            assertThat(service.plan).isEqualTo("a-good-one")
            assertThat(service.broker).isEqualTo("some-broker")
        }

        test("Parses the user provided services config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")

            val userProvidedServices = pushAppsConfig.userProvidedServices
            assertThat(userProvidedServices).hasSize(1)

            val service = userProvidedServices!![0]
            assertThat(service.name).isEqualTo("some-user-provided-service-name")
            assertThat(service.credentials).isEqualTo(mapOf("username" to "some-username"))
        }
    }
})
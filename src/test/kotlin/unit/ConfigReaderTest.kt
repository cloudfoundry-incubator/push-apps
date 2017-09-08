package unit

import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import pushapps.ConfigReader

class ConfigReaderTest: Test({
    describe("#parseConfig") {
        test("Parses the cf config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")

            assertThat(pushAppsConfig.cf.apiHost).isEqualTo("api.example.com")
            assertThat(pushAppsConfig.cf.username).isEqualTo("some-username")
            assertThat(pushAppsConfig.cf.password).isEqualTo("some-password")
            assertThat(pushAppsConfig.cf.organization).isEqualTo("some-organization")
            assertThat(pushAppsConfig.cf.space).isEqualTo("some-space")
            assertThat(pushAppsConfig.cf.skipSslValidation).isTrue()
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
        }

        test("Parses the user provided services config") {
            val pushAppsConfig = ConfigReader.parseConfig("src/test/kotlin/support/exampleConfig.yml")

            val userProvidedServices = pushAppsConfig.userProvidedServices
            assertThat(userProvidedServices).hasSize(1)

            val service1 = userProvidedServices[0]
            assertThat(service1.name).isEqualTo("some-service-name")
            assertThat(service1.credentials).isEqualTo(mapOf("username" to "some-username"))
        }
    }
})
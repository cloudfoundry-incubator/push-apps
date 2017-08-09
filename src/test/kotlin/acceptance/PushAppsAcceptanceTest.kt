package acceptance

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.damo.aspen.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Fail
import org.cloudfoundry.operations.spaces.DeleteSpaceRequest
import pushapps.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class PushAppsAcceptanceTest : Test({
    val apiHost = System.getenv("CF_API")!!
    val username = System.getenv("CF_USERNAME")!!
    val password = System.getenv("CF_PASSWORD")!!
    val organization = "system"

    val cfOperations = cloudFoundryOperationsBuilder()
        .apply {
            this.apiHost = apiHost
            this.username = username
            this.password = password
            this.organization = organization
        }.build()

    val cf = buildCfClient(apiHost, username, password, organization)

    before {
        writeConfigFile(apiHost, username, password, organization, "test")
    }

    after {
        val deleteSpaceRequest = DeleteSpaceRequest
            .builder()
            .name("test")
            .build()
        cfOperations.spaces().delete(deleteSpaceRequest).block()
    }

    describe("pushApps") {
        test("creates space in system org if it doesn't exist") {
            val exitCode = runPushApps()

            val spaces = cf.listSpaces()

            assertThat(spaces).contains("test")
            assertThat(exitCode).isEqualTo(0)
        }

        test("does not create space if it already exists") {
            cf.createSpaceIfDoesNotExist("test")

            val exitCode = runPushApps()
            assertThat(exitCode).isEqualTo(0)
        }
    }
})

val workingDir = System.getProperty("user.dir")!!
val configPath = "$workingDir/src/test/kotlin/acceptance/support/acceptance.yml"

fun writeConfigFile(apiHost: String, username: String, password: String, organization: String, space: String) {
    val cf = Cf(apiHost, username, password, organization, space)
    val metricsApp = App(
        "metrics",
        "$workingDir/src/test/kotlin/acceptance/support/metrics.zip",
        "binary_buildpack",
        mapOf("FOO" to "bar")
    )

    val apps = arrayOf(metricsApp)
    val config = Config(cf, apps)

    val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
    val configFilePath = Paths.get(configPath)
    if (!Files.exists(configFilePath, LinkOption.NOFOLLOW_LINKS)) {
        Files.createFile(configFilePath)
    }

    val configFile = File(configPath)
    objectMapper.writeValue(configFile, config)
}

fun runPushApps(): Int {
    val version = System.getenv("PUSHAPPS_VERSION")!!

    val pushAppsProcess = ProcessBuilder(
        "java",
        "-jar",
        "$workingDir/build/libs/push-apps-$version.jar",
        "-c",
        configPath
    ).inheritIO().start()

    pushAppsProcess.waitFor(30, TimeUnit.SECONDS)

    if (pushAppsProcess.isAlive) {
        Fail.fail("Process failed to finish within timeout window")
    }

    val exitValue = pushAppsProcess.exitValue()
    pushAppsProcess.destroyForcibly()

    return exitValue
}

fun buildCfClient(apiHost: String, username: String, password: String, organization: String): CloudFoundryClient {
    return CloudFoundryClient(
        apiHost,
        username,
        password,
        organization
    )
}
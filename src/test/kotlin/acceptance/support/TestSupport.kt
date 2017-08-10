package acceptance.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Fail
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.organizations.DeleteOrganizationRequest
import org.cloudfoundry.operations.spaces.DeleteSpaceRequest
import pushapps.*
import java.io.File
import java.util.concurrent.TimeUnit

val workingDir = System.getProperty("user.dir")!!

data class TestContext(
    val cfOperations: CloudFoundryOperations,
    val cf: CloudFoundryClient,
    val configFilePath: String
)

fun buildTestContext(organization: String, space: String, apps: Array<AppConfig>): TestContext {
    val apiHost = System.getenv("CF_API")!!
    val username = System.getenv("CF_USERNAME")!!
    val password = System.getenv("CF_PASSWORD")!!

    val cfOperations = cloudFoundryOperationsBuilder()
        .apply {
            this.apiHost = apiHost
            this.username = username
            this.password = password
        }.build()

    val cf = buildCfClient(apiHost, username, password)

    val configFilePath = writeConfigFile(
        apiHost = apiHost,
        username = username,
        password = password,
        organization = organization,
        space = space,
        apps = apps
    )

    return TestContext(cfOperations, cf, configFilePath)
}

fun cleanupCf(cfOperations: CloudFoundryOperations, cfClient: CloudFoundryClient, organization: String, space: String) {
    val organizations = cfClient.listOrganizations()
    if (!organizations.contains(organization)) return

    val newOperations = cloudFoundryOperationsBuilder()
        .fromExistingOperations(cfOperations)
        .apply {
            this.organization = organization
        }.build()

    val deleteSpaceRequest = DeleteSpaceRequest
        .builder()
        .name(space)
        .build()
    newOperations.spaces().delete(deleteSpaceRequest).block()

    val deleteOrganizationRequest = DeleteOrganizationRequest
        .builder()
        .name(organization)
        .build()
    newOperations.organizations().delete(deleteOrganizationRequest).block()
}

fun writeConfigFile(
    apiHost: String,
    username: String,
    password: String,
    organization: String,
    space: String,
    apps: Array<AppConfig>
): String {
    val cf = CfConfig(apiHost, username, password, organization, space)
    val config = Config(cf, apps)

    val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    val tempFile = File.createTempFile("acceptance-test", ".yml")
    objectMapper.writeValue(tempFile, config)

    return tempFile.absolutePath
}

fun runPushApps(configFilePath: String): Int {
    val version = System.getenv("PUSHAPPS_VERSION")!!

    val pushAppsProcess = ProcessBuilder(
        "java",
        "-jar",
        "$workingDir/build/libs/push-apps-$version.jar",
        "-c",
        configFilePath
    ).inheritIO().start()

    pushAppsProcess.waitFor(30, TimeUnit.SECONDS)

    if (pushAppsProcess.isAlive) {
        Fail.fail("Process failed to finish within timeout window")
    }

    val exitValue = pushAppsProcess.exitValue()
    pushAppsProcess.destroyForcibly()

    return exitValue
}

fun buildCfClient(apiHost: String, username: String, password: String): CloudFoundryClient {
    return CloudFoundryClient(
        apiHost,
        username,
        password
    )
}
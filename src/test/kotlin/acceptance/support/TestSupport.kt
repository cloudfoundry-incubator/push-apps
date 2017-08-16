package acceptance.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.sun.javaws.exceptions.InvalidArgumentException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Fail
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.DeleteApplicationRequest
import org.cloudfoundry.operations.organizations.DeleteOrganizationRequest
import org.cloudfoundry.operations.spaces.DeleteSpaceRequest
import pushapps.*
import java.io.File
import java.util.concurrent.TimeUnit

val workingDir = System.getProperty("user.dir")!!

data class TestContext(
    val cfOperations: CloudFoundryOperations,
    val cfClient: CloudFoundryClient,
    val configFilePath: String
)

fun getEnv(name: String): String {
    val env = System.getenv(name)
    if (env === null || env.isEmpty()) {
        throw InvalidArgumentException(arrayOf("must provide a $name for pushapps"))
    }

    return env
}

fun buildTestContext(organization: String, space: String, apps: Array<AppConfig>): TestContext {
    val apiHost = getEnv("CF_API")
    val username = getEnv("CF_USERNAME")
    val password = getEnv("CF_PASSWORD")

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

fun cleanupCf(tc: TestContext?, organization: String, space: String) {
    if (tc === null) {
        return
    }

    val (cfOperations, cfClient, configFilePath) = tc

    val organizations = cfClient.listOrganizations()
    if (!organizations.contains(organization)) return

    cfClient.targetOrganization(organization)

    val spaces = cfClient.listSpaces()
    if (!spaces.contains(space)) return

    cfClient.targetSpace(space)

    val newOperations = cloudFoundryOperationsBuilder()
        .fromExistingOperations(cfOperations)
        .apply {
            this.organization = organization
            this.space = space
        }.build()

    cfClient.listApplications().forEach { applicationSummary ->
        val deleteApplicationRequest = DeleteApplicationRequest
            .builder()
            .name(applicationSummary.name)
            .deleteRoutes(true)
            .build()
        newOperations.applications().delete(deleteApplicationRequest).block()
    }

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

fun runPushApps(configFilePath: String, debug: Boolean = false): Int {
    val version = getEnv("PUSHAPPS_VERSION")

    val pushAppsCommand = mutableListOf("java")

    if (debug) pushAppsCommand.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")

    pushAppsCommand.addAll(
        listOf("-jar",
            "$workingDir/build/libs/push-apps-$version.jar",
            "-c",
            configFilePath
        )
    )

    val pushAppsProcess = ProcessBuilder(
        pushAppsCommand
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


val client = OkHttpClient()

fun httpGet(url: String): Response {

    val request = Request.Builder()
        .url(url)
        .build()

    return client.newCall(request).execute()
}
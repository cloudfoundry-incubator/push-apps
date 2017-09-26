package support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Fail
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.applications.DeleteApplicationRequest
import org.cloudfoundry.operations.organizations.DeleteOrganizationRequest
import org.cloudfoundry.operations.routes.DeleteOrphanedRoutesRequest
import org.cloudfoundry.operations.services.DeleteServiceInstanceRequest
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
        throw Exception("must provide a $name for pushapps")
    }

    return env
}

fun buildTestContext(organization: String,
                     space: String,
                     apps: List<AppConfig>,
                     services: List<ServiceConfig>,
                     userProvidedServices: List<UserProvidedServiceConfig>
): TestContext {
    val apiHost = getEnv("CF_API")
    val username = getEnv("CF_USERNAME")
    val password = getEnv("CF_PASSWORD")

    val cfOperations = cloudFoundryOperationsBuilder()
        .apply {
            this.apiHost = apiHost
            this.username = username
            this.password = password
            this.skipSslValidation = true
        }.build()

    val cf = buildCfClient(apiHost, username, password)

    val configFilePath = writeConfigFile(
        apiHost = apiHost,
        username = username,
        password = password,
        organization = organization,
        space = space,
        apps = apps,
        userProvidedServices = userProvidedServices,
        services = services,
        skipSslValidation = true
    )

    return TestContext(cfOperations, cf, configFilePath)
}

fun cleanupCf(tc: TestContext?, organization: String, space: String) {
    if (tc === null) {
        return
    }

    val (cfOperations, cfClient, _) = tc

    val organizations = cfClient.listOrganizations()
    if (!organizations.contains(organization)) return

    cfClient.createAndTargetOrganization(organization)

    val spaces = cfClient.listSpaces()
    if (!spaces.contains(space)) return

    cfClient.createAndTargetSpace(space)

    val targetedOperations = cloudFoundryOperationsBuilder()
        .fromExistingOperations(cfOperations)
        .apply {
            this.organization = organization
            this.space = space
            this.skipSslValidation = true
        }.build()

    deleteApplications(targetedOperations)

    deleteRoutes(targetedOperations)

    deleteServices(cfClient, targetedOperations)

    deleteSpace(space, targetedOperations)

    deleteOrganization(organization, targetedOperations)
}

private fun deleteApplications(targetedOperations: CloudFoundryOperations) {
    val appNames: List<String> = targetedOperations
        .applications()
        .list()
        .map(ApplicationSummary::getName)
        .toIterable()
        .toList()

    appNames.forEach { appName ->
        val deleteApplicationRequest = DeleteApplicationRequest
            .builder()
            .name(appName)
            .deleteRoutes(true)
            .build()
        targetedOperations.applications().delete(deleteApplicationRequest).block()
    }
}

private fun deleteRoutes(targetedOperations: CloudFoundryOperations) {
    val deleteRouteRequest = DeleteOrphanedRoutesRequest
        .builder()
        .build()
    targetedOperations.routes().deleteOrphanedRoutes(deleteRouteRequest).block()
}

private fun deleteServices(cfClient: CloudFoundryClient, newOperations: CloudFoundryOperations) {
    cfClient.listServices().forEach { serviceName ->
        val deleteServiceInstanceRequest = DeleteServiceInstanceRequest
            .builder()
            .name(serviceName)
            .build()
        newOperations.services().deleteInstance(deleteServiceInstanceRequest).block()
    }
}

private fun deleteSpace(space: String, newOperations: CloudFoundryOperations) {
    val deleteSpaceRequest = DeleteSpaceRequest
        .builder()
        .name(space)
        .build()
    newOperations.spaces().delete(deleteSpaceRequest).block()
}

private fun deleteOrganization(organization: String, newOperations: CloudFoundryOperations) {
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
    apps: List<AppConfig>,
    userProvidedServices: List<UserProvidedServiceConfig>,
    services: List<ServiceConfig>,
    skipSslValidation: Boolean
): String {
    val cf = CfConfig(apiHost, username, password, organization, space, skipSslValidation)
    val config = Config(PushApps(), cf, apps, services, userProvidedServices)

    val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    val tempFile = File.createTempFile("acceptance-test", ".yml")
    objectMapper.writeValue(tempFile, config)

    return tempFile.absolutePath
}

fun runPushApps(configFilePath: String, debug: Boolean = false): Int {
    val version = getEnv("PUSHAPPS_VERSION")

    val pushAppsCommand = mutableListOf("java")

    if (debug) pushAppsCommand.addAll(
        listOf(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
        )
    )

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

    pushAppsProcess.waitFor(2, TimeUnit.MINUTES)

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
        password,
        skipSslValidation = true
    )
}

val client = OkHttpClient()

fun httpGet(url: String): Response {
    val request = Request.Builder()
        .url(url)
        .build()

    return client.newCall(request).execute()
}
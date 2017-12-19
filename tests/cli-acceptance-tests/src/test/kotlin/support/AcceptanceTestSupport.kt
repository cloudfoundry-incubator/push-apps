package support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.kittinunf.fuel.Fuel
import org.apache.commons.io.FilenameUtils
import org.assertj.core.api.Fail
import org.cloudfoundry.client.v2.organizations.DeleteOrganizationRequest
import org.cloudfoundry.client.v2.securitygroups.DeleteSecurityGroupRequest
import org.cloudfoundry.client.v2.securitygroups.ListSecurityGroupsRequest
import org.cloudfoundry.client.v2.securitygroups.ListSecurityGroupsResponse
import org.cloudfoundry.client.v2.securitygroups.SecurityGroupResource
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.organizations.OrganizationDetail
import org.cloudfoundry.operations.organizations.OrganizationInfoRequest
import org.cloudfoundry.tools.pushapps.*
import reactor.core.publisher.Mono
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

data class AcceptanceTestContext(
    val cfOperations: CloudFoundryOperations,
    val cfClient: CloudFoundryClient,
    val configFilePath: String,
    val securityGroups: List<SecurityGroup>?,
    val organization: String
)

class AcceptanceTestSupport {
    val pushAppsRootDir = findRootDir()
    val acceptanceTestProjectDir = findProjectDir()

    private fun findRootDir(): String {
        val compiledFileLocation = DockerSupport::class.java.getResource("${DockerSupport::class.simpleName}.class")
        val projectPath = Paths.get(compiledFileLocation.path.toString(), "../../../../../../../../").toAbsolutePath().toString()
        return FilenameUtils.normalize(projectPath)
    }

    private fun findProjectDir(): String {
        val compiledFileLocation = DockerSupport::class.java.getResource("${DockerSupport::class.simpleName}.class")
        val projectPath = Paths.get(compiledFileLocation.path.toString(), "../../../../../../").toAbsolutePath().toString()
        return FilenameUtils.normalize(projectPath)
    }


    fun getEnv(name: String): String {
        val env = System.getenv(name)
        if (env === null || env.isEmpty()) {
            throw Exception("must provide a $name for pushapps")
        }

        return env
    }

    fun getEnvOrDefault(name: String, default: String): String {
        var env = System.getenv(name)
        if (env === null || env.isEmpty()) {
            env = default
        }

        return env
    }

    fun buildTestContext(
        space: String,
        apps: List<AppConfig> = emptyList(),
        services: List<ServiceConfig> = emptyList(),
        userProvidedServices: List<UserProvidedServiceConfig> = emptyList(),
        migrations: List<Migration> = emptyList(),
        securityGroups: List<SecurityGroup> = emptyList(),
        retryCount: Int,
        maxInFlight: Int
    ): AcceptanceTestContext {
        val organization = "pushapps_test_${UUID.randomUUID().toString()}"

        val apiHost = getEnv("CF_API")
        val username = getEnv("CF_USERNAME")
        val password = getEnv("CF_PASSWORD")

        val cfOperations = cloudFoundryOperationsBuilder()
            .apply {
                this.apiHost = apiHost
                this.username = username
                this.password = password
                this.skipSslValidation = true
                this.organization = organization
            }.build()

        val cf = buildCfClient(apiHost, username, password, organization, retryCount)

        val configFilePath = writeConfigFile(
            apiHost = apiHost,
            username = username,
            password = password,
            organization = organization,
            space = space,
            apps = apps,
            userProvidedServices = userProvidedServices,
            services = services,
            migrations = migrations,
            securityGroups = securityGroups,
            skipSslValidation = true,
            retryCount = retryCount,
            maxInFlight = maxInFlight
        )

        return AcceptanceTestContext(cfOperations, cf, configFilePath, securityGroups, organization)
    }

    fun cleanupCf(tc: AcceptanceTestContext?, organization: String?, space: String) {
        if (tc === null) {
            return
        }

        val cfClient = tc.cfClient

        val organizations = cfClient.listOrganizations().toIterable().toList()
        if (!organizations.contains(organization) || organization === null) return

        cfClient.createAndTargetOrganization(organization)

        val spaces = cfClient.listSpaces().toIterable().toList()
        if (!spaces.contains(space)) return

        cfClient.createAndTargetSpace(space)

        val targetedOperations = getTargetedOperations(tc.cfOperations, organization, space)
        deleteOrganization(organization, targetedOperations)
        deleteSecurityGroups(targetedOperations, tc.securityGroups)

        Files.deleteIfExists(Paths.get(tc.configFilePath))
    }

    private fun deleteOrganization(organizationName: String, newOperations: CloudFoundryOperations) {
        val getOrganizationRequest = OrganizationInfoRequest
            .builder()
            .name(organizationName)
            .build()

        val organizationId = newOperations
            .organizations()
            .get(getOrganizationRequest)
            .map(OrganizationDetail::getId)
            .block()

        val deleteOrganizationRequest = DeleteOrganizationRequest
            .builder()
            .organizationId(organizationId)
            .recursive(true)
            .build()

        val reactiveCloudFoundryClient = (newOperations as DefaultCloudFoundryOperations)
            .cloudFoundryClient

        reactiveCloudFoundryClient
            .organizations()
            .delete(deleteOrganizationRequest)
            .block()
    }

    private fun deleteSecurityGroups(newOperations: CloudFoundryOperations, securityGroups: List<SecurityGroup>?) {
        if (securityGroups === null) return

        val reactiveCloudFoundryClient = (newOperations as DefaultCloudFoundryOperations)
            .cloudFoundryClient

        val securityGroupResources = listSecurityGroupResources(reactiveCloudFoundryClient, securityGroups)

        securityGroupResources.forEach { group ->
            val securityGroupId = group.metadata.id

            val deleteSecurityGroupRequest = DeleteSecurityGroupRequest
                .builder()
                .securityGroupId(securityGroupId)
                .build()

            reactiveCloudFoundryClient
                .securityGroups()
                .delete(deleteSecurityGroupRequest)
                .block()
        }
    }

    fun listSecurityGroupResources(
        reactiveCloudFoundryClient: org.cloudfoundry.client.CloudFoundryClient,
        securityGroups: List<SecurityGroup>
    ): List<SecurityGroupResource> {
        val securityGroupsRequest = ListSecurityGroupsRequest
            .builder()
            .names(securityGroups.map(SecurityGroup::name))
            .build()

        val securityGroupResources = reactiveCloudFoundryClient
            .securityGroups()
            .list(securityGroupsRequest)
            .map(ListSecurityGroupsResponse::getResources)
            .block()
        return securityGroupResources ?: emptyList()
    }

    private fun getTargetedOperations(cfOperations: CloudFoundryOperations, organization: String, space: String): CloudFoundryOperations {
        val targetedOperations = cloudFoundryOperationsBuilder()
            .fromExistingOperations(cfOperations)
            .apply {
                this.organization = organization
                this.space = space
                this.skipSslValidation = true
            }.build()
        return targetedOperations
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
        migrations: List<Migration>,
        securityGroups: List<SecurityGroup>,
        skipSslValidation: Boolean,
        retryCount: Int,
        maxInFlight: Int
    ): String {
        val cf = CfConfig(apiHost, username, password, organization, space, skipSslValidation)
        val config = Config(
            PushAppsConfig(retryCount, maxInFlight),
            cf,
            apps,
            services,
            userProvidedServices,
            migrations,
            securityGroups
        )

        val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

        val tempFile = File.createTempFile("acceptance-test", ".yml")
        objectMapper.writeValue(tempFile, config)

        return tempFile.absolutePath
    }

    fun runPushApps(configFilePath: String, debug: Boolean = false): Int {
        val inputStream: InputStream = File("$pushAppsRootDir/gradle/version.gradle").inputStream()
        val versionFileString = inputStream.bufferedReader().use { it.readText() }

        val version = versionFileString.removePrefix("version = '").removeSuffix("'\n")

        val pushAppsCommand = mutableListOf("java")

        if (debug) pushAppsCommand.addAll(
            listOf(
                "-agentlib:jdwp=transport=dt_socket,server=n,address=127.0.0.1:5005,suspend=y"
            )
        )

        pushAppsCommand.addAll(
            listOf("-jar",
                "$pushAppsRootDir/applications/cli/build/libs/push-apps-$version.jar",
                "-c",
                configFilePath
            )
        )

        val pushAppsProcessBuilder = ProcessBuilder(
            pushAppsCommand
        ).inheritIO()

        if (debug) pushAppsProcessBuilder.environment().put("LOG_LEVEL", "debug")

        val pushAppsProcess = pushAppsProcessBuilder.start()
        pushAppsProcess.waitFor(5, TimeUnit.MINUTES)

        if (pushAppsProcess.isAlive) {
            Fail.fail("Process failed to finish within timeout window")
        }

        val exitValue = pushAppsProcess.exitValue()
        pushAppsProcess.destroyForcibly()

        return exitValue
    }

    fun buildCfClient(apiHost: String, username: String, password: String, organization: String, retryCount: Int): CloudFoundryClient {
        val config = CfConfig(
            apiHost = apiHost,
            username = username,
            password = password,
            skipSslValidation = true,
            organization = organization,
            space = ""
        )

        val cloudFoundryOperations = cloudFoundryOperationsBuilder()
            .apply {
                this.apiHost = config.apiHost
                this.username = config.username
                this.password = config.password
                this.skipSslValidation = config.skipSslValidation
                this.dialTimeoutInMillis = config.dialTimeoutInMillis
                this.organization = config.organization
            }.build()

        return CloudFoundryClient(cloudFoundryOperations, cloudFoundryOperationsBuilder(), 5L, retryCount)
    }

    fun httpGet(url: String): Mono<String> {
        return Mono.create<String> { sink ->
            Fuel.get(url).responseString { _, _, result ->
                result.fold(sink::success, sink::error)
            }
        }
    }
}

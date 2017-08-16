package pushapps

import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.applications.PushApplicationRequest
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest
import org.cloudfoundry.operations.applications.StartApplicationRequest
import org.cloudfoundry.operations.organizations.CreateOrganizationRequest
import org.cloudfoundry.operations.organizations.OrganizationSummary
import org.cloudfoundry.operations.spaces.CreateSpaceRequest
import org.cloudfoundry.operations.spaces.SpaceSummary
import java.io.File
import java.util.concurrent.CompletableFuture

class CloudFoundryClient(
    apiHost: String,
    username: String,
    password: String
) {

    private var cloudFoundryOperations = cloudFoundryOperationsBuilder()
        .apply {
            this.apiHost = apiHost
            this.username = username
            this.password = password
        }
        .build()

    fun listApplications(): MutableIterable<ApplicationSummary> {
        return cloudFoundryOperations.applications().list().toIterable()
    }

    fun pushApplication(appConfig: AppConfig): CompletableFuture<Boolean> {
        val pushRequest = PushApplicationRequest
            .builder()
            .name(appConfig.name)
            .buildpack(appConfig.buildpack)
            .command(appConfig.command)
            .path(File(appConfig.path).toPath())
            .noStart(true)
            .build()

        val pushAppFuture = cloudFoundryOperations
            .applications()
            .push(pushRequest)
            .toFuture()

        return pushAppFuture.thenApply {
            generateSetEnvFutures(appConfig.name, appConfig.environment)
        }.thenCompose { setEnvFutures ->
            CompletableFuture.allOf(*setEnvFutures.toTypedArray())
        }.thenCompose {
            startApplication(appConfig.name)
        }.thenApply {
            true
        }.exceptionally { _ -> false }
    }

    fun startApplication(applicationName: String): CompletableFuture<Void> {
        val startApplicationRequest = StartApplicationRequest.builder().name(applicationName).build()
        return cloudFoundryOperations.applications().start(startApplicationRequest).toFuture()
    }

    fun createOrganizationIfDoesNotExist(name: String) {
        if (!organizationDoesExist(name)) {
            createOrganization(name)
        }
    }

    fun listOrganizations(): List<String> {
        val orgFlux = cloudFoundryOperations
            .organizations()
            .list()
            .map(OrganizationSummary::getName)

        return orgFlux
            .toIterable()
            .toList()
    }

    fun targetOrganization(organizationName: String): CloudFoundryClient {
        cloudFoundryOperations = cloudFoundryOperationsBuilder()
            .fromExistingOperations(cloudFoundryOperations)
            .apply {
                this.organization = organizationName
            }.build()

        return this
    }

    fun createSpaceIfDoesNotExist(name: String) {
        if (!spaceDoesExist(name)) {
            createSpace(name)
        }
    }

    fun listSpaces(): List<String> {
        val spaceFlux = cloudFoundryOperations
            .spaces()
            .list()
            .map(SpaceSummary::getName)

        return spaceFlux
            .toIterable()
            .toList()
    }

    fun targetSpace(space: String): CloudFoundryClient {
        cloudFoundryOperations = cloudFoundryOperationsBuilder()
            .fromExistingOperations(cloudFoundryOperations)
            .apply {
                this.space = space
            }.build()

        return this
    }

    private fun organizationDoesExist(name: String) = listOrganizations().indexOf(name) != -1

    private fun createOrganization(name: String) {
        val createOrganizationRequest: CreateOrganizationRequest = CreateOrganizationRequest
            .builder()
            .organizationName(name)
            .build()

        cloudFoundryOperations.organizations().create(createOrganizationRequest).block()
    }

    private fun spaceDoesExist(name: String) = listSpaces().indexOf(name) != -1

    private fun createSpace(name: String) {
        val createSpaceRequest: CreateSpaceRequest = CreateSpaceRequest
            .builder()
            .name(name)
            .build()

        cloudFoundryOperations.spaces().create(createSpaceRequest).block()
    }

    private fun generateSetEnvFutures(applicationName: String, environment: Map<String, String>?): List<CompletableFuture<Void>> {
        val setEnvRequests = generateSetEnvRequests(applicationName, environment)

        return setEnvRequests.map { request ->
            cloudFoundryOperations
                .applications()
                .setEnvironmentVariable(request)
                .toFuture()
        }
    }

    private fun generateSetEnvRequests(applicationName: String, environment: Map<String, String>?): List<SetEnvironmentVariableApplicationRequest> {
        if (environment === null) return emptyList()

        return environment.map { variable ->
            SetEnvironmentVariableApplicationRequest
                .builder()
                .name(applicationName)
                .variableName(variable.key)
                .variableValue(variable.value)
                .build()
        }
    }
}
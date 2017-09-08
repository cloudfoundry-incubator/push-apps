package pushapps

import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.organizations.CreateOrganizationRequest
import org.cloudfoundry.operations.organizations.OrganizationSummary
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest
import org.cloudfoundry.operations.services.ServiceInstanceSummary
import org.cloudfoundry.operations.spaces.CreateSpaceRequest
import org.cloudfoundry.operations.spaces.SpaceSummary
import java.util.concurrent.CompletableFuture


class CloudFoundryClient(
    apiHost: String,
    username: String,
    password: String,
    skipSslValidation: Boolean = false
) {

    private var cloudFoundryOperations = cloudFoundryOperationsBuilder()
        .apply {
            this.apiHost = apiHost
            this.username = username
            this.password = password
            this.skipSslValidation = skipSslValidation
        }
        .build()

    fun createUserProvidedService(serviceConfig: UserProvidedServiceConfig): CompletableFuture<OperationResult> {
        val createServiceRequest = CreateUserProvidedServiceInstanceRequest
            .builder()
            .name(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        return cloudFoundryOperations
            .services()
            .createUserProvidedInstance(createServiceRequest)
            .toFuture()
            .thenApply {
                OperationResult(
                    name = serviceConfig.name,
                    didSucceed = true
                )
            }.exceptionally { error ->
            OperationResult(
                name = serviceConfig.name,
                didSucceed = false,
                error = error
            )
        }
    }

    fun deployApplication(appConfig: AppConfig): CompletableFuture<OperationResult> {
        val appDeploy = DeployApplication(cloudFoundryOperations, appConfig)
        return appDeploy.deploy()
    }

    fun createAndTargetOrganization(cf: CfConfig): CloudFoundryClient {
        createOrganizationIfDoesNotExist(cf.organization)
        return targetOrganization(cf.organization)
    }

    fun createOrganizationIfDoesNotExist(name: String) {
        if (!organizationDoesExist(name)) {
            createOrganization(name)
        }
    }

    private fun organizationDoesExist(name: String) = listOrganizations().indexOf(name) != -1

    private fun createOrganization(name: String) {
        val createOrganizationRequest: CreateOrganizationRequest = CreateOrganizationRequest
            .builder()
            .organizationName(name)
            .build()

        cloudFoundryOperations.organizations().create(createOrganizationRequest).block()
    }

    fun createAndTargetSpace(cf: CfConfig): CloudFoundryClient {
        createSpaceIfDoesNotExist(cf.space)
        return targetSpace(cf.space)
    }

    fun createSpaceIfDoesNotExist(name: String) {
        if (!spaceDoesExist(name)) {
            createSpace(name)
        }
    }

    private fun spaceDoesExist(name: String) = listSpaces().indexOf(name) != -1

    private fun createSpace(name: String) {
        val createSpaceRequest: CreateSpaceRequest = CreateSpaceRequest
            .builder()
            .name(name)
            .build()

        cloudFoundryOperations.spaces().create(createSpaceRequest).block()
    }

    fun listApplications(): MutableIterable<ApplicationSummary> {
        return cloudFoundryOperations.applications().list().toIterable()
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

    fun listSpaces(): List<String> {
        val spaceFlux = cloudFoundryOperations
            .spaces()
            .list()
            .map(SpaceSummary::getName)

        return spaceFlux
            .toIterable()
            .toList()
    }

    fun listServices(): List<String> {
        val serviceInstanceFlux = cloudFoundryOperations
            .services()
            .listInstances()
            .map(ServiceInstanceSummary::getName)

        return serviceInstanceFlux
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

    fun targetSpace(space: String): CloudFoundryClient {
        cloudFoundryOperations = cloudFoundryOperationsBuilder()
            .fromExistingOperations(cloudFoundryOperations)
            .apply {
                this.space = space
            }.build()

        return this
    }
}
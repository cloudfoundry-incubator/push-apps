package org.cloudfoundry.tools.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.client.v2.securitygroups.CreateSecurityGroupRequest
import org.cloudfoundry.client.v2.securitygroups.Protocol
import org.cloudfoundry.client.v2.securitygroups.RuleEntity
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.*
import org.cloudfoundry.operations.organizations.CreateOrganizationRequest
import org.cloudfoundry.operations.organizations.OrganizationSummary
import org.cloudfoundry.operations.routes.MapRouteRequest
import org.cloudfoundry.operations.routes.UnmapRouteRequest
import org.cloudfoundry.operations.services.*
import org.cloudfoundry.operations.spaces.CreateSpaceRequest
import org.cloudfoundry.operations.spaces.GetSpaceRequest
import org.cloudfoundry.operations.spaces.SpaceDetail
import org.cloudfoundry.operations.spaces.SpaceSummary
import org.cloudfoundry.tools.pushapps.config.AppConfig
import org.cloudfoundry.tools.pushapps.config.SecurityGroup
import org.cloudfoundry.tools.pushapps.config.ServiceConfig
import org.cloudfoundry.tools.pushapps.config.UserProvidedServiceConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

class CloudFoundryClient(
    private var cloudFoundryOperations: CloudFoundryOperations,
    private val cloudFoundryOperationsBuilder: CloudFoundryOperationsBuilder,
    private val operationTimeoutInMinutes: Long,
    private val retryCount: Int

) {
    private val logger = LogManager.getLogger(CloudFoundryClient::class.java)

    private fun <T> buildMonoCfOperationWithRetries(operation: () -> Mono<T>, numberOfRetries: Int): Mono<T> {
        if (numberOfRetries == 0) {
            return operation().timeout(Duration.ofMinutes(operationTimeoutInMinutes))
        }

        return operation()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .onErrorResume { error ->
                logger.debug("Resuming operation due to error ${error.message}")
                buildMonoCfOperationWithRetries(operation, numberOfRetries - 1)
            }
    }

    private fun <T> buildFluxCfOperationWithRetries(operation: () -> Flux<T>, numberOfRetries: Int): Flux<T> {
        if (numberOfRetries == 0) {
            return operation().timeout(Duration.ofMinutes(operationTimeoutInMinutes))
        }

        return operation()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .onErrorResume { error ->
                logger.debug("Resuming operation due to error ${error.message}")
                buildFluxCfOperationWithRetries(operation, numberOfRetries - 1)
            }
    }

    fun createService(serviceConfig: ServiceConfig): Mono<Void> {
        val createServiceRequest = CreateServiceInstanceRequest
            .builder()
            .serviceInstanceName(serviceConfig.name)
            .planName(serviceConfig.plan)
            .serviceName(serviceConfig.broker)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .services()
                .createInstance(createServiceRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun createUserProvidedService(serviceConfig: UserProvidedServiceConfig): Mono<Void> {
        val createServiceRequest = CreateUserProvidedServiceInstanceRequest
            .builder()
            .name(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .services()
                .createUserProvidedInstance(createServiceRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun updateUserProvidedService(serviceConfig: UserProvidedServiceConfig): Mono<Void> {
        val updateServiceRequest = UpdateUserProvidedServiceInstanceRequest
            .builder()
            .userProvidedServiceInstanceName(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .services()
                .updateUserProvidedInstance(updateServiceRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun pushApplication(appConfig: AppConfig): Mono<Void> {
        val pushApplication = PushApplication(cloudFoundryOperations, appConfig)
        val cfOperation = { pushApplication.generatePushAppAction() }
        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun startApplication(appName: String): Mono<Void> {
        val startApplicationRequest = StartApplicationRequest
            .builder()
            .name(appName)
            .stagingTimeout(Duration.ofMinutes(10))
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .applications()
                .start(startApplicationRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun stopApplication(appName: String): Mono<Void> {
        val stopApplicationRequest = StopApplicationRequest
            .builder()
            .name(appName)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .applications()
                .stop(stopApplicationRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun setApplicationEnvironment(appConfig: AppConfig): Mono<Void> {
        val setEnvRequests = generateSetEnvRequests(appConfig)

        return setEnvRequests.foldRight(Mono.empty<Void>(), { request, memo ->
            val cfOperation = {
                cloudFoundryOperations
                    .applications()
                    .setEnvironmentVariable(request)
            }

            memo.then(buildMonoCfOperationWithRetries(cfOperation, retryCount))
        })
    }

    private fun generateSetEnvRequests(appConfig: AppConfig): List<SetEnvironmentVariableApplicationRequest> {
        if (appConfig.environment === null) return emptyList()

        return appConfig.environment.map { variable ->
            if (variable.value.isEmpty()) {
                logger.debug("Setting environment variable ${variable.key} to empty string")
            }

            SetEnvironmentVariableApplicationRequest
                .builder()
                .name(appConfig.name)
                .variableName(variable.key)
                .variableValue(variable.value)
                .build()
        }
    }

    //FIXME: should this just return one action at a time?
    fun bindServicesToApplication(appName: String, serviceNames: List<String>): List<Mono<Void>> {
        val bindServiceRequests = generateBindServiceRequests(appName, serviceNames)

        return bindServiceRequests.map { request ->
            val cfOperation = {
                cloudFoundryOperations
                    .services()
                    .bind(request)
            }

            buildMonoCfOperationWithRetries(cfOperation, retryCount)
        }
    }

    private fun generateBindServiceRequests(appName: String, serviceNames: List<String>): List<BindServiceInstanceRequest> {
        return serviceNames.map { serviceName ->
            BindServiceInstanceRequest
                .builder()
                .applicationName(appName)
                .serviceInstanceName(serviceName)
                .build()
        }
    }

    fun mapRoute(appConfig: AppConfig): Mono<Void> {
        if (appConfig.route === null) {
            return Mono.empty()
        }

        //FIXME return error mono if domain, hostname, or path don't exist
        var route = "http://${appConfig.route.hostname}.${appConfig.domain}"

        val mapRouteRequestBuilder = MapRouteRequest
            .builder()
            .applicationName(appConfig.name)
            .domain(appConfig.domain)
            .host(appConfig.route.hostname)

        if (appConfig.route.path !== null) {
            mapRouteRequestBuilder.path(appConfig.route.path)
            route += "/${appConfig.route.path}"
        }

        val mapRouteRequest = mapRouteRequestBuilder.build()

        logger.debug("Building request to map route $route for application ${appConfig.name}")

        val cfOperation = {
            cloudFoundryOperations
                .routes()
                .map(mapRouteRequest)
                .ofType(Void.TYPE)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun unmapRoute(appConfig: AppConfig): Mono<Void> {
        if (appConfig.route === null) {
            return Mono.empty()
        }

        val route = "http://${appConfig.route.hostname}.${appConfig.domain}"
        logger.debug("Building request to unmap route $route for application ${appConfig.name}")

        val unmapRouteRequest = UnmapRouteRequest
            .builder()
            .applicationName(appConfig.name)
            .domain(appConfig.domain)
            .host(appConfig.route.hostname)
            .path(appConfig.route.path)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .routes()
                .unmap(unmapRouteRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun createSecurityGroup(securityGroup: SecurityGroup, spaceId: String): Mono<Void> {
        val rule = RuleEntity
            .builder()
            .destination(securityGroup.destination)
            .protocol(Protocol.from(securityGroup.protocol))
            .build()

        val createSecurityGroupRequest = CreateSecurityGroupRequest
            .builder()
            .name(securityGroup.name)
            .rule(rule)
            .spaceId(spaceId)
            .build()

        val defaultCloudFoundryOperations = cloudFoundryOperations as DefaultCloudFoundryOperations
        val cfOperation = {
            defaultCloudFoundryOperations.cloudFoundryClient
                .securityGroups()
                .create(createSecurityGroupRequest)
                .ofType(Void.TYPE)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    fun createAndTargetOrganization(organizationName: String): CloudFoundryClient {
        //FIXME: Can we chain these together to avoid blocking?
        createOrganizationIfDoesNotExist(organizationName)
        return targetOrganization(organizationName)
    }

    private fun createOrganizationIfDoesNotExist(name: String) {
        if (!organizationDoesExist(name)) {
            createOrganization(name).block()
        }
    }

    private fun organizationDoesExist(name: String) = listOrganizations().toIterable().indexOf(name) != -1

    private fun createOrganization(name: String): Mono<Void> {
        val createOrganizationRequest: CreateOrganizationRequest = CreateOrganizationRequest
            .builder()
            .organizationName(name)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .organizations()
                .create(createOrganizationRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    private fun targetOrganization(organizationName: String): CloudFoundryClient {
        cloudFoundryOperations = cloudFoundryOperationsBuilder
            .fromExistingOperations(cloudFoundryOperations)
            .apply {
                this.organization = organizationName
            }.build()

        return this
    }

    fun createAndTargetSpace(spaceName: String): CloudFoundryClient {
        createSpaceIfDoesNotExist(spaceName)
        return targetSpace(spaceName)
    }

    private fun createSpaceIfDoesNotExist(name: String) {
        if (!spaceDoesExist(name)) {
            createSpace(name).block()
        }
    }

    private fun spaceDoesExist(name: String) = listSpaces().toIterable().toList().indexOf(name) != -1

    private fun createSpace(name: String): Mono<Void> {
        val createSpaceRequest: CreateSpaceRequest = CreateSpaceRequest
            .builder()
            .name(name)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .spaces()
                .create(createSpaceRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
    }

    private fun targetSpace(space: String): CloudFoundryClient {
        cloudFoundryOperations = cloudFoundryOperationsBuilder
            .fromExistingOperations(cloudFoundryOperations)
            .apply {
                this.space = space
            }.build()

        return this
    }

    fun listOrganizations(): Flux<String> {
        val cfOperation = {
            cloudFoundryOperations
                .organizations()
                .list()
        }

        return buildFluxCfOperationWithRetries(cfOperation, retryCount)
            .map(OrganizationSummary::getName)
    }

    fun listSpaces(): Flux<String> {
        val cfOperation = {
            cloudFoundryOperations
                .spaces()
                .list()
        }

        return buildFluxCfOperationWithRetries(cfOperation, retryCount)
            .map(SpaceSummary::getName)
    }

    fun listServices(): Flux<String> {
        val cfOperation = {
            cloudFoundryOperations
                .services()
                .listInstances()
        }
        return buildFluxCfOperationWithRetries(cfOperation, retryCount)
            .map(ServiceInstanceSummary::getName)
    }

    fun listApplications(): Flux<String> {
        val cfOperation = {
            cloudFoundryOperations
                .applications()
                .list()
        }

        return buildFluxCfOperationWithRetries(cfOperation, retryCount)
            .map(ApplicationSummary::getName)
    }

    fun getSpaceId(spaceName: String): Mono<String> {
        val spaceRequest = GetSpaceRequest
            .builder()
            .name(spaceName)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .spaces()
                .get(spaceRequest)
        }

        return buildMonoCfOperationWithRetries(cfOperation, retryCount)
            .map(SpaceDetail::getId)
    }

    fun fetchRecentLogsForAsync(appName: String): Flux<LogMessage> {
        val logsRequest = LogsRequest
            .builder()
            .name(appName)
            .recent(true)
            .build()

        val cfOperation = {
            cloudFoundryOperations
                .applications()
                .logs(logsRequest)
        }

        return buildFluxCfOperationWithRetries(cfOperation, retryCount)
    }
}

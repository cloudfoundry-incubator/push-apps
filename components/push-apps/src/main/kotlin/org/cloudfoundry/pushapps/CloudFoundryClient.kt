package org.cloudfoundry.pushapps

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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

class CloudFoundryClient(
    private var cloudFoundryOperations: CloudFoundryOperations,
    private val cloudFoundryOperationsBuilder: CloudFoundryOperationsBuilder,
    private val operationTimeoutInMinutes: Long

) {
    private val logger = LogManager.getLogger(CloudFoundryClient::class.java)

    fun createService(serviceConfig: ServiceConfig): Mono<Void> {
        val createServiceRequest = CreateServiceInstanceRequest
            .builder()
            .serviceInstanceName(serviceConfig.name)
            .planName(serviceConfig.plan)
            .serviceName(serviceConfig.broker)
            .build()

        return cloudFoundryOperations
            .services()
            .createInstance(createServiceRequest)
    }

    fun createUserProvidedService(serviceConfig: UserProvidedServiceConfig): Mono<Void> {
        val createServiceRequest = CreateUserProvidedServiceInstanceRequest
            .builder()
            .name(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        return cloudFoundryOperations
            .services()
            .createUserProvidedInstance(createServiceRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
    }

    fun updateUserProvidedService(serviceConfig: UserProvidedServiceConfig): Mono<Void> {
        val updateServiceRequest = UpdateUserProvidedServiceInstanceRequest
            .builder()
            .userProvidedServiceInstanceName(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        return cloudFoundryOperations
            .services()
            .updateUserProvidedInstance(updateServiceRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
    }

    fun pushApplication(appConfig: AppConfig): Mono<Void> {
        val pushApplication = PushApplication(cloudFoundryOperations, appConfig)
        return pushApplication.generatePushAppAction()
    }

    fun startApplication(appName: String): Mono<Void> {
        val startApplicationRequest = StartApplicationRequest
            .builder()
            .name(appName)
            .stagingTimeout(Duration.ofMinutes(10))
            .build()
        return cloudFoundryOperations
            .applications()
            .start(startApplicationRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
    }

    fun stopApplication(appName: String): Mono<Void> {
        val stopApplicationRequest = StopApplicationRequest
            .builder()
            .name(appName)
            .build()

        return cloudFoundryOperations
            .applications()
            .stop(stopApplicationRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
    }

    fun setApplicationEnvironment(appConfig: AppConfig): Mono<Void> {
        val setEnvRequests = generateSetEnvRequests(appConfig)

        return setEnvRequests.foldRight(Mono.empty<Void>(), { request, memo ->
            val setEnvVar = cloudFoundryOperations
                .applications()
                .setEnvironmentVariable(request)
                .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            memo.then(setEnvVar)
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
            cloudFoundryOperations
                .services()
                .bind(request)
                .timeout(Duration.ofMinutes(this.operationTimeoutInMinutes))
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

        //TODO return error mono if domain, hostname, or path don't exist
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
        return cloudFoundryOperations
            .routes()
            .map(mapRouteRequest)
            .ofType(Void.TYPE)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
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

        return cloudFoundryOperations
            .routes()
            .unmap(unmapRouteRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
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
        return defaultCloudFoundryOperations.cloudFoundryClient
            .securityGroups()
            .create(createSecurityGroupRequest)
            .ofType(Void.TYPE)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
    }

    fun createAndTargetOrganization(organizationName: String): CloudFoundryClient {
        createOrganizationIfDoesNotExist(organizationName)
        return targetOrganization(organizationName)
    }

    private fun createOrganizationIfDoesNotExist(name: String) {
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

        cloudFoundryOperations
            .organizations()
            .create(createOrganizationRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .block() //FIXME: remove blocking statement if possible
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
            createSpace(name)
        }
    }

    private fun spaceDoesExist(name: String) = listSpaces().indexOf(name) != -1

    private fun createSpace(name: String) {
        val createSpaceRequest: CreateSpaceRequest = CreateSpaceRequest
            .builder()
            .name(name)
            .build()

        cloudFoundryOperations
            .spaces()
            .create(createSpaceRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .block() //FIXME: remove blocking statement if possible
    }

    private fun targetSpace(space: String): CloudFoundryClient {
        cloudFoundryOperations = cloudFoundryOperationsBuilder
            .fromExistingOperations(cloudFoundryOperations)
            .apply {
                this.space = space
            }.build()

        return this
    }

    fun listOrganizations(): List<String> {
        val orgFlux = cloudFoundryOperations
            .organizations()
            .list()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .map(OrganizationSummary::getName)

        return orgFlux
            .toIterable() //FIXME: remove blocking statement if possible
            .toList()
    }

    fun listSpaces(): List<String> {
        val spaceFlux = cloudFoundryOperations
            .spaces()
            .list()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .map(SpaceSummary::getName)

        return spaceFlux
            .toIterable() //FIXME: remove blocking statement if possible
            .toList()
    }

    fun listServices(): List<String> {
        val serviceInstanceFlux = cloudFoundryOperations
            .services()
            .listInstances()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .map(ServiceInstanceSummary::getName)

        return serviceInstanceFlux
            .toIterable() //FIXME: remove blocking statement if possible
            .toList()
    }

    fun listApplications(): List<String> {
        val applicationListFlux = cloudFoundryOperations
            .applications()
            .list()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .map(ApplicationSummary::getName)

        return applicationListFlux.toIterable().toList()
    }

    fun getSpaceId(spaceName: String): Mono<String> {
        val spaceRequest = GetSpaceRequest
            .builder()
            .name(spaceName)
            .build()

        return cloudFoundryOperations
            .spaces()
            .get(spaceRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .map(SpaceDetail::getId)
    }

    fun fetchRecentLogsForAsync(appName: String): Flux<LogMessage> {
        val logsRequest = LogsRequest
            .builder()
            .name(appName)
            .recent(true)
            .build()

        return cloudFoundryOperations
            .applications()
            .logs(logsRequest)
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
    }
}

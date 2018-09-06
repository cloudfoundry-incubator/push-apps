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
import org.cloudfoundry.operations.stacks.Stack
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

    private fun <T> buildMonoCfOperationWithRetries(numberOfRetries: Int, operation: () -> Mono<T>): Mono<T> {
        if (numberOfRetries == 0) {
            return operation()
                .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
        }

        return operation()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes)) //TODO: this does not seem to work
            .onErrorResume { error ->
                logger.debug("Resuming operation due to error ${error.message}")
                buildMonoCfOperationWithRetries(numberOfRetries - 1, operation)
            }
    }

    private fun <T> buildFluxCfOperationWithRetries(numberOfRetries: Int, operation: () -> Flux<T>): Flux<T> {
        if (numberOfRetries == 0) {
            return operation().timeout(Duration.ofMinutes(operationTimeoutInMinutes))
        }

        return operation()
            .timeout(Duration.ofMinutes(operationTimeoutInMinutes))
            .onErrorResume { error ->
                logger.debug("Resuming operation due to error ${error.message}")
                buildFluxCfOperationWithRetries(numberOfRetries - 1, operation)
            }
    }

    fun createService(serviceConfig: ServiceConfig): Mono<Void> {
        val createServiceRequest = CreateServiceInstanceRequest
            .builder()
            .serviceInstanceName(serviceConfig.name)
            .planName(serviceConfig.plan)
            .serviceName(serviceConfig.broker)
            .build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .services()
                .createInstance(createServiceRequest)
        }
    }

    fun createUserProvidedService(serviceConfig: UserProvidedServiceConfig): Mono<Void> {
        val createServiceRequest = CreateUserProvidedServiceInstanceRequest
            .builder()
            .name(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .services()
                .createUserProvidedInstance(createServiceRequest)
        }
    }

    fun updateUserProvidedService(serviceConfig: UserProvidedServiceConfig): Mono<Void> {
        val updateServiceRequest = UpdateUserProvidedServiceInstanceRequest
            .builder()
            .userProvidedServiceInstanceName(serviceConfig.name)
            .credentials(serviceConfig.credentials)
            .build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .services()
                .updateUserProvidedInstance(updateServiceRequest)
        }
    }

    fun pushApplication(appConfig: AppConfig): Mono<Void> {
        val pushApplication = PushApplication(cloudFoundryOperations, appConfig, listStacks())
        return buildMonoCfOperationWithRetries(retryCount) {
            pushApplication.generatePushAppAction()
        }
    }

    fun startApplication(appName: String): Mono<Void> {
        val startApplicationRequest = StartApplicationRequest
            .builder()
            .name(appName)
            .stagingTimeout(Duration.ofMinutes(10))
            .build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .applications()
                .start(startApplicationRequest)
        }
    }

    fun stopApplication(appName: String): Mono<Void> {
        val stopApplicationRequest = StopApplicationRequest
            .builder()
            .name(appName)
            .build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .applications()
                .stop(stopApplicationRequest)
        }
    }

    fun setApplicationEnvironment(appConfig: AppConfig): Mono<Void> {
        val setEnvRequests = generateSetEnvRequests(appConfig)

        return setEnvRequests.foldRight(Mono.empty<Void>(), { request, memo ->
            val monoCfOperationWithRetries = buildMonoCfOperationWithRetries(retryCount) {
                cloudFoundryOperations
                    .applications()
                    .setEnvironmentVariable(request)
            }

            memo.then(monoCfOperationWithRetries)
        })
    }

    private fun generateSetEnvRequests(appConfig: AppConfig): List<SetEnvironmentVariableApplicationRequest> {
        if (appConfig.environment === null) return emptyList()

        return appConfig.environment.map { variable ->
            val value = variable.value ?: return@map null

            if (value.isEmpty()) {
                logger.debug("Setting environment variable ${variable.key} to empty string")
            }

            SetEnvironmentVariableApplicationRequest
                .builder()
                .name(appConfig.name)
                .variableName(variable.key)
                .variableValue(value)
                .build()
        }.filterNotNull()
    }

    //FIXME: should this just return one action at a time?
    fun bindServicesToApplication(appName: String, serviceNames: List<String>): List<Mono<Void>> {
        val bindServiceRequests = generateBindServiceRequests(appName, serviceNames)

        return bindServiceRequests.map { request ->
            buildMonoCfOperationWithRetries(retryCount) {
                cloudFoundryOperations
                    .services()
                    .bind(request)
            }
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

        //FIXME: return error mono if domain, hostname, or path don't exist
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

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .routes()
                .map(mapRouteRequest)
                .ofType(Void.TYPE)
        }
    }

    fun unmapRoute(appConfig: AppConfig): Mono<Void> {
        if (appConfig.route === null) {
            return Mono.empty()
        }

        val unmapRouteRequestBuilder = UnmapRouteRequest
            .builder()
            .applicationName(appConfig.name)
            .domain(appConfig.domain)
            .host(appConfig.route.hostname)

        if (appConfig.route.path !== null) {
            unmapRouteRequestBuilder.path(appConfig.route.path)
        }

        val unmapRouteRequest = unmapRouteRequestBuilder.build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .routes()
                .unmap(unmapRouteRequest)
        }
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

        return buildMonoCfOperationWithRetries(retryCount) {
            defaultCloudFoundryOperations.cloudFoundryClient
                .securityGroups()
                .create(createSecurityGroupRequest)
                .ofType(Void.TYPE)
        }
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

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .organizations()
                .create(createOrganizationRequest)
        }
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

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .spaces()
                .create(createSpaceRequest)
        }
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
        return buildFluxCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .organizations()
                .list()
        }.map(OrganizationSummary::getName)
    }

    fun listSpaces(): Flux<String> {
        return buildFluxCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .spaces()
                .list()
        }.map(SpaceSummary::getName)
    }

    fun listServices(): Flux<String> {
        return buildFluxCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .services()
                .listInstances()
        }.map(ServiceInstanceSummary::getName)
    }

    fun listApplications(): Flux<String> {
        return buildFluxCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .applications()
                .list()
        }.map(ApplicationSummary::getName)
    }

    fun getSpaceId(spaceName: String): Mono<String> {
        val spaceRequest = GetSpaceRequest
            .builder()
            .name(spaceName)
            .build()

        return buildMonoCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .spaces()
                .get(spaceRequest)
        }.map(SpaceDetail::getId)
    }

    fun fetchRecentLogsForAsync(appName: String): Flux<LogMessage> {
        val logsRequest = LogsRequest
            .builder()
            .name(appName)
            .recent(true)
            .build()

        return buildFluxCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                .applications()
                .logs(logsRequest)
        }
    }

    fun listStacks(): Flux<String> {
        return buildFluxCfOperationWithRetries(retryCount) {
            cloudFoundryOperations
                    .stacks()
                    .list()
        }.map(Stack::getName)
    }
}

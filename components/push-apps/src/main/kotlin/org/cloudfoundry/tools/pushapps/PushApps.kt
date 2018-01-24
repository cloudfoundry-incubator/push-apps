package org.cloudfoundry.tools.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.UnknownCloudFoundryException
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.tools.pushapps.config.*
import org.flywaydb.core.Flyway
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.component1
import reactor.util.function.component2

class PushApps(
        private val config: Config,
        private val cloudFoundryClientBuilder: CloudFoundryClientBuilder,
        private val flywayWrapper: FlywayWrapper = FlywayWrapper({ Flyway() }),
        private val dataSourceFactory: DataSourceFactory = DataSourceFactory(
        { mySqlDataSourceBuilder(it) },
        { postgresDataSourceBuilder(it) }
    )
) {
    private val logger = LogManager.getLogger(PushApps::class.java)

    fun pushApps(): Boolean {
        val (pushAppsConfig, cf, apps, services, userProvidedServices, migrations, securityGroups) = config
        val targetedCfClientGenerator = buildTargetedCfGenerator(
            cf = cf,
            cfOperationTimeoutInMinutes = pushAppsConfig.cfOperationTimeoutInMinutes,
            retryCount = pushAppsConfig.operationRetryCount
        )

        val createSecurityGroups: Flux<OperationResult> = targetedCfClientGenerator
            .next()
            .flatMap { cloudFoundryClient ->
                cloudFoundryClient.getSpaceId(cf.space)
            }.switchIfEmpty(Mono.error(PushAppsError("Could not find space id for space ${cf.space}")))
            .zipWith(targetedCfClientGenerator.next())
            .flatMapMany { (spaceId, cloudFoundryClient) ->
                securityGroups
                    .createSecurityGroupsFlux(cloudFoundryClient, cf, pushAppsConfig, spaceId)
            }

        val runMigrations: Flux<OperationResult> = migrations.runMigrationsFlux(
            maxInFlight = pushAppsConfig.maxInFlight,
            retryCount = pushAppsConfig.operationRetryCount,
            timeoutInMinutes = pushAppsConfig.migrationTimeoutInMinutes
        )

        val servicesAvailable: Flux<String> = targetedCfClientGenerator
            .next()
            .flatMapMany { cloudFoundryClient ->
                services.createServices(
                    cloudFoundryClient = cloudFoundryClient,
                    maxInFlight = pushAppsConfig.maxInFlight,
                    retryCount = pushAppsConfig.operationRetryCount
                )
            }

        val userProvidedServicesAvailable: Flux<String> = targetedCfClientGenerator
            .next()
            .flatMapMany { cloudFoundryClient ->
                userProvidedServices.createOrUpdateUserProvidedServices(
                    cloudFoundryClient = cloudFoundryClient,
                    maxInFlight = pushAppsConfig.maxInFlight,
                    retryCount = pushAppsConfig.operationRetryCount
                )
            }

        val deployApps: Flux<OperationResult> = servicesAvailable
            .mergeWith(userProvidedServicesAvailable)
            .collectList()
            .zipWith(targetedCfClientGenerator.next())
            .flatMap { (allServicesAvailable, cloudFoundryClient) ->
                apps.deployApps(
                    availableServices = allServicesAvailable.toList(),
                    maxInFlight = pushAppsConfig.maxInFlight,
                    retryCount = pushAppsConfig.operationRetryCount,
                    cloudFoundryClient = cloudFoundryClient
                ).collectList()
            }
            .flatMapMany { results ->
                Flux.fromIterable(results)
            }

        val result = Flux
            .concat(createSecurityGroups, runMigrations, deployApps)
            .then(Mono.just(true))
            .doOnError { error ->
                logger.error(error.message)
                if (logger.isDebugEnabled) {
                    error.printStackTrace()
                }
            }
            .onErrorReturn(false)
            .block()

        if (result === null) {
            return false
        }

        return result
    }

    private fun List<SecurityGroup>.createSecurityGroupsFlux(
            cloudFoundryClient: CloudFoundryClient,
            cf: CfConfig,
            pushAppsConfig: PushAppsConfig,
            spaceId: String
    ): Flux<OperationResult> {
        if (isEmpty()) return Flux.empty()

        val createSecurityGroups: Flux<OperationResult> = createSecurityGroups(
            cloudFoundryClient,
            cf.space,
            pushAppsConfig.maxInFlight,
            pushAppsConfig.operationRetryCount,
            spaceId
        )

        return handleOperationResults(createSecurityGroups, "Create security group")
    }

    private fun buildTargetedCfGenerator(
            cf: CfConfig,
            cfOperationTimeoutInMinutes: Long,
            retryCount: Int
    ): Flux<CloudFoundryClient> {
        return Mono.fromSupplier {
            val cloudFoundryOperations = cloudFoundryOperationsBuilder()
                .apply {
                    this.apiHost = cf.apiHost
                    this.username = cf.username
                    this.password = cf.password
                    this.skipSslValidation = cf.skipSslValidation
                    this.dialTimeoutInMillis = cf.dialTimeoutInMillis
                }
                .build()

            val cloudFoundryClient = cloudFoundryClientBuilder.apply {
                this.cloudFoundryOperations = cloudFoundryOperations
                this.operationTimeoutInMinutes = cfOperationTimeoutInMinutes
                this.retryCount = retryCount
            }.build()

            cloudFoundryClient
                .createAndTargetOrganization(cf.organization)
                .createAndTargetSpace(cf.space)
        }.repeat()
    }

    private fun List<SecurityGroup>.createSecurityGroups(
        cloudFoundryClient: CloudFoundryClient,
        space: String,
        maxInFlight: Int,
        retryCount: Int,
        spaceId: String
    ): Flux<OperationResult> {
        val securityGroupCreator = SecurityGroupCreator(
            this,
            cloudFoundryClient,
            space,
            maxInFlight,
            retryCount
        )
        return securityGroupCreator.createSecurityGroups(spaceId)
    }

    private fun List<ServiceConfig>.createServices(
        cloudFoundryClient: CloudFoundryClient,
        maxInFlight: Int,
        retryCount: Int
    ): Flux<String> {
        if (isEmpty()) return Flux.fromIterable(emptyList())

        val serviceCreator = ServiceCreator(
            serviceConfigs = this,
            cloudFoundryClient = cloudFoundryClient,
            maxInFlight = maxInFlight,
            retryCount = retryCount
        )

        return serviceCreator
            .createServices()
            .flatMap { result ->
                handleOperationResult(result, "Create service")
            }
            .filter(OperationResult::didSucceed)
            .flatMap { result ->
                Flux.just(result.operationConfig.name)
            }
    }

    private fun List<UserProvidedServiceConfig>.createOrUpdateUserProvidedServices(
        cloudFoundryClient: CloudFoundryClient,
        maxInFlight: Int,
        retryCount: Int
    ): Flux<String> {
        if (isEmpty()) return Flux.fromIterable(emptyList())

        val userProvidedServiceCreator = UserProvidedServiceCreator(
            cloudFoundryClient = cloudFoundryClient,
            serviceConfigs = this,
            maxInFlight = maxInFlight,
            retryCount = retryCount
        )

        return userProvidedServiceCreator
            .createOrUpdateServices()
            .flatMap { result ->
                handleOperationResult(result, "Create user provided service")
            }
            .filter(OperationResult::didSucceed)
            .flatMap { result ->
                Flux.just(result.operationConfig.name)
            }
    }

    private fun List<Migration>.runMigrationsFlux(
        maxInFlight: Int,
        retryCount: Int,
        timeoutInMinutes: Long
    ): Flux<OperationResult> {
        if (isEmpty()) return Flux.empty<OperationResult>()

        val databaseMigrationResults = DatabaseMigrator(
            this,
            flywayWrapper,
            dataSourceFactory,
            maxInFlight = maxInFlight,
            retryCount = retryCount,
            timeoutInMinutes = timeoutInMinutes
        ).migrate()

        return handleOperationResults(databaseMigrationResults, "Migrating database")
    }

    private fun List<AppConfig>.deployApps(
        availableServices: List<String>,
        maxInFlight: Int,
        retryCount: Int,
        cloudFoundryClient: CloudFoundryClient
    ): Flux<OperationResult> {
        val appDeployer = org.cloudfoundry.tools.pushapps.AppDeployer(cloudFoundryClient, this, availableServices, maxInFlight, retryCount)
        val results = appDeployer.deployApps()

        return handleOperationResults(results, "Deploying application")
    }

    private fun handleOperationResults(results: Flux<OperationResult>, actionName: String): Flux<OperationResult> {
        return results.flatMap { result ->
            handleOperationResult(result, actionName)
        }
    }

    private fun handleOperationResult(result: OperationResult, actionName: String): Flux<OperationResult> {
        val (name, config, didSucceed, error, recentLogs) = result

        if (didSucceed) return Flux.just(result)

        if (config.optional) {
            logger.warn("$actionName $name was optional and failed with error message: ${error!!.message}")
            return Flux.just(result)
        }

        val messages = mutableListOf<String>()

        if (error !== null) {
            messages.add(error.message!!)

            val cause = error.cause
            when (cause) {
                is UnknownCloudFoundryException -> messages.add("UnknownCloudFoundryException thrown with a statusCode:${cause.statusCode}, and message: ${cause.message}")
                is IllegalStateException -> messages.add("IllegalStateException with message: ${cause.message}")
            }

            if (logger.isDebugEnabled) {
                error.printStackTrace()
            }
        }

        logger.error("$actionName $name failed with error messages: [${messages.joinToString(", ")}]")

        val logs = recentLogs
            .toIterable()
            .sortedByDescending(LogMessage::getTimestamp)
            .map(LogMessage::getMessage)
            .toList()

        if (logs.isNotEmpty()) {
            val failedDeploymentLogLinesToShow = Math.min(logs.size - 1, this.config.pushApps.failedDeploymentLogLinesToShow)
            val logsToShow = logs.subList(0, failedDeploymentLogLinesToShow)
            logger.error("Deployment of $name failed, printing the most recent $failedDeploymentLogLinesToShow log lines")
            logger.error(logsToShow.joinToString("\n\r"))
        } else {
            logger.error("Unable to fetch logs for failed operation $name")
        }

        throw PushAppsError("Non-optional operation $name failed", error)
    }
}

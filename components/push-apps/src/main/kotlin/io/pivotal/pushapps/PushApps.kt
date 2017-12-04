package io.pivotal.pushapps

import io.pivotal.pushapps.CloudFoundryOperationsBuilder.Companion.cloudFoundryOperationsBuilder
import org.apache.logging.log4j.LogManager
import org.cloudfoundry.UnknownCloudFoundryException
import org.cloudfoundry.doppler.LogMessage
import org.flywaydb.core.Flyway

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

        val cloudFoundryClient = targetCf(cf)

        if (securityGroups.isNotEmpty()) {
            val success = securityGroups.createSecurityGroups(
                cloudFoundryClient,
                cf.space,
                pushAppsConfig.maxInFlight,
                pushAppsConfig.appDeployRetryCount
            )

            if (!success) return false
        }

        var availableServices: List<String> = emptyList()
        if (services.isNotEmpty()) {
            val (success, servicesAvailable) = services.createServices(cloudFoundryClient)
            if (!success) return false
            availableServices = servicesAvailable
        }

        if (userProvidedServices.isNotEmpty()) {
            val userProvidedServiceNames = userProvidedServices.map(UserProvidedServiceConfig::name)
            val success = userProvidedServices.createOrUpdateUserProvidedServices(
                cloudFoundryClient,
                pushAppsConfig.maxInFlight,
                pushAppsConfig.appDeployRetryCount
            )
            if (!success) return false
            availableServices += userProvidedServiceNames
        }

        if (migrations.isNotEmpty()) {
            val success = migrations.runMigrations(
                pushAppsConfig.maxInFlight,
                pushAppsConfig.appDeployRetryCount
            )
            if (!success) return false
        }

        return apps.deployApps(
            availableServices = availableServices,
            maxInFlight = pushAppsConfig.maxInFlight,
            retryCount = pushAppsConfig.appDeployRetryCount,
            cloudFoundryClient = cloudFoundryClient
        )
    }

    private fun targetCf(cf: CfConfig): CloudFoundryClient {
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
        }.build()

        return cloudFoundryClient
            .createAndTargetOrganization(cf.organization)
            .createAndTargetSpace(cf.space)
    }

    private fun List<SecurityGroup>.createSecurityGroups(
        cloudFoundryClient: CloudFoundryClient,
        space: String,
        maxInFlight: Int,
        retryCount: Int
    ): Boolean {
        val securityGroupCreator = SecurityGroupCreator(
            this,
            cloudFoundryClient,
            space,
            maxInFlight,
            retryCount
        )
        val results = securityGroupCreator.createSecurityGroups()

        return handleOperationResult(results, "Creating security group")
    }

    private fun List<ServiceConfig>.createServices(cloudFoundryClient: CloudFoundryClient): Pair<Boolean, List<String>> {
        val serviceCreator = ServiceCreator(cloudFoundryClient, this)
        val createServiceResults = serviceCreator.createServices()

        val success = handleOperationResult(createServiceResults, "Creating service")
        val createdServices = createServiceResults
            .filter(OperationResult::didSucceed)
            .map(OperationResult::name)

        return Pair(success, createdServices)
    }

    private fun List<UserProvidedServiceConfig>.createOrUpdateUserProvidedServices(
        cloudFoundryClient: CloudFoundryClient,
        maxInFlight: Int,
        retryCount: Int
    ): Boolean {
        val userProvidedServiceCreator = UserProvidedServiceCreator(
            cloudFoundryClient = cloudFoundryClient,
            serviceConfigs = this,
            maxInFlight = maxInFlight,
            retryCount = retryCount
        )
        val createUserServicesResults = userProvidedServiceCreator.createOrUpdateServices()

        return handleOperationResult(createUserServicesResults, "Creating user provided service")
    }

    private fun List<Migration>.runMigrations(
        maxInFlight: Int,
        retryCount: Int
    ): Boolean {
        val databaseMigrationResults = DatabaseMigrator(
            this,
            flywayWrapper,
            dataSourceFactory,
            maxInFlight = maxInFlight,
            retryCount = retryCount
        ).migrate()

        return handleOperationResult(databaseMigrationResults, "Migrating database")
    }

    private fun List<AppConfig>.deployApps(
        availableServices: List<String>,
        maxInFlight: Int,
        retryCount: Int,
        cloudFoundryClient: CloudFoundryClient
    ): Boolean {
        val appDeployer = AppDeployer(cloudFoundryClient, this, availableServices, maxInFlight, retryCount)
        val results = appDeployer.deployApps()

        return handleOperationResult(results, "Deploying application")
    }

    private fun handleOperationResult(results: List<OperationResult>, actionName: String): Boolean {
        val didSucceed = didSucceed(results)
        if (!didSucceed) {
            return handleOperationFailure(results, actionName)
        }

        return true
    }

    private fun didSucceed(results: List<OperationResult>): Boolean {
        return results.fold(true, { acc, result -> acc && result.didSucceed })
    }

    private fun handleOperationFailure(results: List<OperationResult>, actionName: String): Boolean {
        val failedResults = results.filterNot(OperationResult::didSucceed)

        failedResults
            .filter(OperationResult::optional)
            .forEach { (name, _, error, _) ->
                logger.warn("$actionName $name was optional and failed with error message: ${error!!.message}")
            }

        val nonOptionalFailedResults = failedResults.filterNot(OperationResult::optional)

        nonOptionalFailedResults
            .forEach { (name, _, error, _, recentLogs) ->
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
                    val failedDeploymentLogLinesToShow = Math.min(logs.size - 1, config.pushApps.failedDeploymentLogLinesToShow)
                    val logsToShow = logs.subList(0, failedDeploymentLogLinesToShow)
                    logger.error("Deployment of $name failed, printing the most recent $failedDeploymentLogLinesToShow log lines")
                    logger.error(logsToShow.joinToString("\n\r"))
                } else {
                    logger.error("Unable to fetch logs for failed operation $name")
                }
            }

        return nonOptionalFailedResults.isEmpty()
    }
}

package io.pivotal.pushapps

import io.pivotal.pushapps.CloudFoundryOperationsBuilder.Companion.cloudFoundryOperationsBuilder
import org.apache.logging.log4j.LogManager
import org.cloudfoundry.UnknownCloudFoundryException
import org.cloudfoundry.doppler.LogMessage
import org.flywaydb.core.Flyway

class PushApps(
    val config: Config,
    private val cloudFoundryClientBuilder: CloudFoundryClientBuilder,
    private val flyway: Flyway = Flyway(),
    private val dataSourceFactory: DataSourceFactory = DataSourceFactory(
        { mySqlDataSourceBuilder(it) },
        { postgresDataSourceBuilder(it) }
    )
) {
    private val logger = LogManager.getLogger(PushApps::class.java)

    fun pushApps(): Boolean {
        val (pushAppsConfig, cf, apps, services, userProvidedServices, migrations, securityGroups) = config

        val cloudFoundryClient = targetCf(cf)

        if (securityGroups !== null) {
            val securityGroupNames = securityGroups.map(SecurityGroup::name)
            logger.info("Creating security groups: ${securityGroupNames.joinToString(", ")}.")
            val success = createSecurityGroups(securityGroups, cloudFoundryClient, cf.space)
            if (!success) return false
        }

        var availableServices: List<String> = emptyList()
        if (services !== null) {
            val serviceNames = services.map(ServiceConfig::name)
            logger.info("Creating services: ${serviceNames.joinToString(", ")}.")
            val (success, servicesAvailable) = createServices(services, cloudFoundryClient)
            if (!success) return false
            availableServices = servicesAvailable
        }

        if (userProvidedServices !== null) {
            val userProvidedServiceNames = userProvidedServices.map(UserProvidedServiceConfig::name)
            logger.info("Creating user provided services: ${userProvidedServiceNames.joinToString(", ")}")
            val success = createOrUpdateUserProvidedServices(userProvidedServices, cloudFoundryClient)
            if (!success) return false
            availableServices += userProvidedServiceNames
        }

        if (migrations !== null) {
            val migrationDescriptions = migrations.map { "${it.driver} migration for schema ${it.schema}" }
            logger.info("Running migrations: ${migrationDescriptions.joinToString(", ")}")
            val success = runMigrations(migrations)
            if (!success) return false
        }

        return deployApps(apps, availableServices, pushAppsConfig.maxInFlight, pushAppsConfig.appDeployRetryCount, cloudFoundryClient)
    }

    private fun createSecurityGroups(securityGroups: List<SecurityGroup>, cloudFoundryClient: CloudFoundryClient, space: String): Boolean {
        val securityGroupCreator = SecurityGroupCreator(securityGroups, cloudFoundryClient, space)
        val results = securityGroupCreator.createSecurityGroups()

        return handleOperationResult(results, "Creating security group")
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

    private fun createServices(services: List<ServiceConfig>, cloudFoundryClient: CloudFoundryClient): Pair<Boolean, List<String>> {
        val serviceCreator = ServiceCreator(cloudFoundryClient, services)
        val createServiceResults = serviceCreator.createServices()

        val success = handleOperationResult(createServiceResults, "Creating service")
        val createdServices = createServiceResults.filter(OperationResult::didSucceed).map(OperationResult::name)

        return Pair(success, createdServices)
    }

    private fun createOrUpdateUserProvidedServices(userProvidedServices: List<UserProvidedServiceConfig>, cloudFoundryClient: CloudFoundryClient): Boolean {
        val userProvidedServiceCreator = UserProvidedServiceCreator(cloudFoundryClient, userProvidedServices)
        val createUserServicesResults = userProvidedServiceCreator.createOrUpdateServices()

        return handleOperationResult(createUserServicesResults, "Creating user provided service")
    }

    private fun runMigrations(migrations: List<Migration>): Boolean {
        val databaseMigrationResults = DatabaseMigrator(
            migrations.toTypedArray(),
            flyway,
            dataSourceFactory
        ).migrate()

        return handleOperationResult(databaseMigrationResults, "Migrating database")
    }

    private fun deployApps(apps: List<AppConfig>, availableServices: List<String>, maxInFlight: Int, retryCount: Int, cloudFoundryClient: CloudFoundryClient): Boolean {
        val appDeployer = AppDeployer(cloudFoundryClient, apps, availableServices, maxInFlight, retryCount)
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
                    val failedDeploymentLogLinesToShow = config.pushApps.failedDeploymentLogLinesToShow
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

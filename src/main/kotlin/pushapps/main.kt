package pushapps

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

val logger: Logger = LoggerFactory.getLogger("Push Apps")

fun main(args: Array<String>) {
    val configPath = ArgumentParser.parseConfigPath(args)
    val (pushApps, cf, apps, services, userProvidedServices, migrations, securityGroups) = ConfigReader.parseConfig(configPath)

    //TODO capture errors and print
    val cloudFoundryClient = targetCf(cf)

    if (securityGroups !== null) {
        createSecurityGroups(cloudFoundryClient, cf, securityGroups)
    }

    var availableServices: List<String> = emptyList()
    if (services !== null) {
        availableServices = createServices(services, cloudFoundryClient)
    }

    if (userProvidedServices !== null) {
        createOrUpdateUserProvidedServices(userProvidedServices, cloudFoundryClient)
        availableServices += userProvidedServices.map(UserProvidedServiceConfig::name)
    }

    if (migrations !== null) {
        runMigrations(migrations)
    }

    deployApps(apps, availableServices, pushApps.appDeployRetryCount, cloudFoundryClient)

    logger.info("SUCCESS")
}

private fun createSecurityGroups(cloudFoundryClient: CloudFoundryClient, cf: CfConfig, securityGroups: List<SecurityGroup>) {
    //TODO get failure output in a sane way
    val spaceId = cloudFoundryClient.getSpaceId(cf.space)
    val securityGroupFutures: List<CompletableFuture<Void>> = securityGroups.map { group ->
        cloudFoundryClient.createSecurityGroup(group, spaceId).toFuture()
    }
    CompletableFuture.allOf(*securityGroupFutures.toTypedArray()).get()
}

private fun targetCf(cf: CfConfig): CloudFoundryClient {
    val cloudFoundryClient = CloudFoundryClient(cf)

    return cloudFoundryClient
        .createAndTargetOrganization(cf.organization)
        .createAndTargetSpace(cf.space)
}

fun createServices(services: List<ServiceConfig>, cloudFoundryClient: CloudFoundryClient): List<String> {
    val serviceCreator = ServiceCreator(cloudFoundryClient, services)
    val createServiceResults = serviceCreator.createServices()

    handleOperationResult(createServiceResults, "Creating service")

    return createServiceResults.filter(OperationResult::didSucceed).map(OperationResult::name)
}

private fun createOrUpdateUserProvidedServices(userProvidedServices: List<UserProvidedServiceConfig>, cloudFoundryClient: CloudFoundryClient) {
    val userProvidedServiceCreator = UserProvidedServiceCreator(cloudFoundryClient, userProvidedServices)
    val createUserServicesResults = userProvidedServiceCreator.createOrUpdateServices()

    handleOperationResult(createUserServicesResults, "Creating user provided service")
}

private fun runMigrations(migrations: List<Migration>) {
    val databaseMigrationResults = DatabaseMigrator(migrations.toTypedArray()).migrate()

    handleOperationResult(databaseMigrationResults, "Migrating database")
}

private fun deployApps(apps: List<AppConfig>, availableServices: List<String>, retryCount: Int, cloudFoundryClient: CloudFoundryClient) {
    val appDeployer = AppDeployer(cloudFoundryClient, apps, availableServices, retryCount)
    val results = appDeployer.deployApps()

    handleOperationResult(results, "Deploying application")
}

private fun handleOperationResult(results: List<OperationResult>, actionName: String) {
    val didSucceed = didSucceed(results)
    if (!didSucceed) {
        handleOperationFailure(results, actionName)
    }
}

private fun didSucceed(results: List<OperationResult>): Boolean {
    val didSucceed = results.fold(true, { acc, result -> acc && result.didSucceed })
    return didSucceed
}

private fun handleOperationFailure(results: List<OperationResult>, actionName: String) {
    val failedResults = results.filterNot(OperationResult::didSucceed)

    failedResults
        .filter(OperationResult::optional)
        .forEach { (name, _, error, _) ->
            logger.warn("$actionName $name was optional and failed with error message: ${error!!.message}")
        }

    val nonOptionalFailedResults = failedResults.filterNot(OperationResult::optional)

    nonOptionalFailedResults
        .forEach { (name, _, error, _) ->
            logger.error("$actionName $name failed with error message: ${error!!.message}")

            if (logger.isDebugEnabled) {
                error.printStackTrace()
            }
        }

    if (nonOptionalFailedResults.isNotEmpty()) {
        System.exit(3)
    }
}

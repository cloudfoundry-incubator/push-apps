package pushapps

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val logger: Logger = LoggerFactory.getLogger("Push Apps")

    val configPath = ArgumentParser.parseConfigPath(args)
    val (cf, apps, userProvidedServices) = ConfigReader.parseConfig(configPath)

    val cloudFoundryClient = targetCf(cf)

    if (apps.isEmpty()) {
        System.exit(0)
    }

    createUserProvidedServices(userProvidedServices, cloudFoundryClient, logger)

    deployApps(apps, cloudFoundryClient, logger)
}

private fun targetCf(cf: CfConfig): CloudFoundryClient {
    val cloudFoundryClient = CloudFoundryClient(
        cf.apiHost,
        cf.username,
        cf.password,
        cf.skipSslValidation
    )

    return cloudFoundryClient
        .createAndTargetOrganization(cf.organization)
        .createAndTargetSpace(cf.space)
}

private fun createUserProvidedServices(userProvidedServices: List<UserProvidedServiceConfig>, cloudFoundryClient: CloudFoundryClient, logger: Logger) {
    val userProvidedServiceCreator = UserProvidedServiceCreator(cloudFoundryClient, userProvidedServices)
    val createUserServicesResults = userProvidedServiceCreator.createOrUpdateServices()

    val didSucceed = didSucceed(createUserServicesResults)
    if (!didSucceed) {
        handleOperationFailure(createUserServicesResults, "Creating user provided service", logger)
    }
}

private fun deployApps(apps: List<AppConfig>, cloudFoundryClient: CloudFoundryClient, logger: Logger) {
    val appDeployer = AppDeployer(cloudFoundryClient, apps)
    val results = appDeployer.deployApps()

    val didSucceed = didSucceed(results)
    if (!didSucceed) {
        handleOperationFailure(results, "Deploying application", logger)
    }
}

private fun handleOperationFailure(results: List<OperationResult>, actionName: String, logger: Logger) {
    results
        .filterNot(OperationResult::didSucceed)
        .forEach { (name, _, error) ->
            logger.error("$actionName $name failed with error message: ${error!!.message}")

            if (logger.isDebugEnabled) {
                error.printStackTrace()
            }
        }

    System.exit(3)
}

private fun didSucceed(results: List<OperationResult>): Boolean {
    val didSucceed = results.fold(true, { acc, result -> acc && result.didSucceed })
    return didSucceed
}

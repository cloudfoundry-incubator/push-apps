package pushapps

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    val logger: Logger = LoggerFactory.getLogger("Push Apps")

    val configPath = ArgumentParser.parseConfigPath(args)
    val (cf, apps) = ConfigReader.parseConfig(configPath)

    val cloudFoundryClient = targetCf(cf)

    if (apps.isEmpty()) {
        System.exit(0)
    }

    val results = createPushAppFlux(apps, cloudFoundryClient)
        .toIterable()
        .toList()

    val didSucceed = results.fold(true, { acc, result -> acc && result.didSucceed })

    if (!didSucceed) {
        results
            .filterNot(DeployResult::didSucceed)
            .forEach { (appName, _, error) ->
                logger.error("Deploying application $appName failed with error message: ${error!!.message}")

                if (logger.isDebugEnabled) {
                    error.printStackTrace()
                }
            }

        System.exit(3)
    }
}

private fun targetCf(cf: CfConfig): CloudFoundryClient {
    val cloudFoundryClient = CloudFoundryClient(
        cf.apiHost,
        cf.username,
        cf.password,
        cf.skipSslValidation
    )

    return cloudFoundryClient
        .createAndTargetOrganization(cf)
        .createAndTargetSpace(cf)
}

private fun createPushAppFlux(apps: List<AppConfig>, cloudFoundryClient: CloudFoundryClient): Flux<DeployResult> {
    return Flux.create { sink ->
        val applicationDeployments = apps.map {
            cloudFoundryClient
                .deployApplication(it)
                .thenApply {
                    sink.next(it)
                }
        }

        CompletableFuture.allOf(*applicationDeployments
            .toTypedArray())
            .thenApply { sink.complete() }
    }
}
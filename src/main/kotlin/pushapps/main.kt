package pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    val configPath = ArgumentParser.parseConfigPath(args)
    val (cf, apps) = ConfigReader.parseConfig(configPath)

    val cloudFoundryClient = targetCf(cf)

    if (apps.isEmpty()) {
        System.exit(0)
    }

    val results = createPushAppFlux(apps, cloudFoundryClient)
        .toIterable()
        .toList()
    val didSucceed = results.reduceRight({ acc, result -> acc && result })

    if (!didSucceed) System.exit(3)
}

private fun targetCf(cf: CfConfig): CloudFoundryClient {
    val cloudFoundryClient = CloudFoundryClient(
        cf.apiHost,
        cf.username,
        cf.password
    )

    return cloudFoundryClient
        .createAndTargetOrganization(cf)
        .createAndTargetSpace(cf)
}

private fun createPushAppFlux(apps: List<AppConfig>, cloudFoundryClient: CloudFoundryClient): Flux<Boolean> {
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
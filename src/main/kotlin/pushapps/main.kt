package pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

fun main(args: Array<String>) {
    val configPath = ArgumentParser.parseConfigPath(args)
    val (cf, apps) = ConfigReader.parseConfig(configPath)

    val cloudFoundryClient = CloudFoundryClient(
        cf.apiHost,
        cf.username,
        cf.password
    )

    cloudFoundryClient.createOrganizationIfDoesNotExist(cf.organization)
    cloudFoundryClient.targetOrganization(cf.organization)

    cloudFoundryClient.createSpaceIfDoesNotExist(cf.space)
    cloudFoundryClient.targetSpace(cf.space)

    if (apps.isEmpty()) {
        System.exit(0)
    }

    val results = createPushAppFlux(apps, cloudFoundryClient).toIterable().toList()
    val didSucceed = results.reduceRight({ acc, result -> acc && result })

    if (!didSucceed) System.exit(127)
}

fun createPushAppFlux(apps: List<AppConfig>, cloudFoundryClient: CloudFoundryClient): Flux<Boolean> {
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
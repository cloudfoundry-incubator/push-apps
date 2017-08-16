package pushapps

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

    val pushApps = apps.map { cloudFoundryClient.deployApplication(it) }.toTypedArray()

    val didSucceed = CompletableFuture.allOf(*pushApps).thenApply {
        pushApps.map(CompletableFuture<Boolean>::get)
            .reduceRight({ acc, result -> acc && result })
    }.get()

    if (!didSucceed) System.exit(127)
}
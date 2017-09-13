package pushapps

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

class AppDeployer(
    private val cloudFoundryClient: CloudFoundryClient,
    private val appConfigs: List<AppConfig>
) {
    fun deployApps(): List<OperationResult> {
        val deployAppsFlux: Flux<OperationResult> = Flux.create { sink ->
            val applicationDeployments = appConfigs.map { appConfig ->
                deployApplication(appConfig)
                    .thenApply {
                        sink.next(it)
                    }
            }

            CompletableFuture.allOf(*applicationDeployments
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        return deployAppsFlux.toIterable().toList()
    }

    private fun deployApplication(appConfig: AppConfig): CompletableFuture<OperationResult> {
        val deployAppFuture = if (appConfig.blueGreenDeploy == true) {
            generateBlueGreenDeployApplicationFuture(appConfig)
        } else {
            generateDeployApplicationFuture(appConfig)
        }

        return deployAppFuture
            .thenApply {
                OperationResult(
                    name = appConfig.name,
                    didSucceed = true
                )
            }.exceptionally { error ->
            OperationResult(
                name = appConfig.name,
                didSucceed = false,
                error = error
            )
        }
    }

    private fun generateBlueGreenDeployApplicationFuture(appConfig: AppConfig): CompletableFuture<Void> {
        val blueAppConfig = appConfig.copy(name = appConfig.name + "-blue")

        return generateDeployApplicationFuture(blueAppConfig)
            .thenCompose { generateDeployApplicationFuture(appConfig) }
            .thenCompose { cloudFoundryClient.stopApplication(blueAppConfig.name).toFuture() }
    }

    private fun generateDeployApplicationFuture(appConfig: AppConfig): CompletableFuture<Void> {
        val pushAppAction = generatePushAppAction(appConfig)
        val setEnvActions = cloudFoundryClient.setApplicationEnvironment(appConfig)
        val bindServiceActions = cloudFoundryClient.bindServicesToApplication(appConfig)

        val pushAppWithEnvFuture = setEnvActions
            .fold(pushAppAction.toFuture()) { acc, setEnvAction ->
                acc.thenCompose { setEnvAction.toFuture() }
            }

        val deployAppFuture = bindServiceActions
            .fold(pushAppWithEnvFuture) { acc, bindServiceAction ->
                acc.thenCompose { bindServiceAction.toFuture() }
            }

        return deployAppFuture.thenCompose { cloudFoundryClient.startApplication(appConfig.name).toFuture() }
    }

    private fun generatePushAppAction(appConfig: AppConfig): Mono<Void> {
        return cloudFoundryClient.pushApplication(appConfig)
    }
}
package pushapps

import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

class AppDeployer(
    private val cloudFoundryClient: CloudFoundryClient,
    private val appConfigs: List<AppConfig>,
    private val availableServices: List<String>,
    private val retryCount: Int
) {
    fun deployApps(): List<OperationResult> {
        val deployAppsFlux: Flux<OperationResult> = Flux.create { sink ->
            val applicationDeployments = appConfigs.map { appConfig ->
                deployApplicationWithRetries(appConfig, sink, 1)
            }

            CompletableFuture.allOf(*applicationDeployments
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        return deployAppsFlux.toIterable().toList()
    }

    private fun deployApplicationWithRetries(appConfig: AppConfig, sink: FluxSink<OperationResult>, attemptCount: Int): CompletableFuture<Any> {
        return deployApplication(appConfig)
            .thenApply {
                if (it.didSucceed || attemptCount >= retryCount) {
                    sink.next(it)
                } else {
                    deployApplicationWithRetries(appConfig, sink, attemptCount + 1)
                }
            }
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
            .thenCompose { cloudFoundryClient.unmapRoute(blueAppConfig).toFuture() }
            .thenCompose { cloudFoundryClient.stopApplication(blueAppConfig.name).toFuture() }
    }

    private fun generateDeployApplicationFuture(appConfig: AppConfig): CompletableFuture<Void> {
        val pushAppAction = generatePushAppAction(appConfig)
        val setEnvActions = cloudFoundryClient.setApplicationEnvironment(appConfig)

        val pushAppWithEnvFuture = setEnvActions
            .fold(pushAppAction.toFuture()) { acc, setEnvAction ->
                acc.thenCompose { setEnvAction.toFuture() }
            }

        val bindServiceActions: List<Mono<Void>> = generateBindServiceActions(appConfig)
        val deployAppFuture = bindServiceActions
            .fold(pushAppWithEnvFuture) { acc, bindServiceAction ->
                acc.thenCompose { bindServiceAction.toFuture() }
            }

        return deployAppFuture
            .thenCompose { cloudFoundryClient.startApplication(appConfig.name).toFuture() }
            .thenCompose { cloudFoundryClient.mapRoute(appConfig).toFuture() }
    }

    private fun generatePushAppAction(appConfig: AppConfig): Mono<Void> {
        return cloudFoundryClient.pushApplication(appConfig)
    }

    private fun generateBindServiceActions(appConfig: AppConfig): List<Mono<Void>> {
        var bindServiceActions: List<Mono<Void>> = emptyList()
        if (appConfig.serviceNames !== null) {
            val availableServicesToBeBound = appConfig.serviceNames.filter {
                availableServices.contains(it)
            }
            bindServiceActions = cloudFoundryClient.bindServicesToApplication(appConfig.name, availableServicesToBeBound)
        }
        return bindServiceActions
    }
}

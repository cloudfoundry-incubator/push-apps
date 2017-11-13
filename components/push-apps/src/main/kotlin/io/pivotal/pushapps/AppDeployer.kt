package io.pivotal.pushapps

import io.netty.util.concurrent.CompleteFuture
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
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
    private val logger: Logger = LogManager.getLogger(AppDeployer::class.java)

    fun deployApps(): List<OperationResult> {
        val appNames = appConfigs.map(AppConfig::name)
        logger.info("Deploying applications: ${appNames.joinToString(", ")}")

        val deployAppsFlux: Flux<OperationResult> = Flux.create { sink ->
            val applicationDeployments = appConfigs.map { appConfig ->
                deployApplicationWithRetries(appConfig, sink, 1)
            }

            CompletableFuture.allOf(*applicationDeployments.toTypedArray())
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

        return getOperationResult(deployAppFuture, appConfig.name, false)
    }

    private fun generateBlueGreenDeployApplicationFuture(appConfig: AppConfig): CompletableFuture<Void> {
        val applications = cloudFoundryClient.listApplications()
        val blueAppConfig = appConfig.copy(name = appConfig.name + "-blue")

        var deployApplicationFuture = generateDeployApplicationFuture(blueAppConfig)

        if (applications.contains(appConfig.name)) {
            deployApplicationFuture = deployApplicationFuture.thenCompose {
                cloudFoundryClient.unmapRoute(appConfig).toFuture()
            }
        }

        return deployApplicationFuture
            .thenCompose { generateDeployApplicationFuture(appConfig) }
            .thenCompose { cloudFoundryClient.unmapRoute(blueAppConfig).toFuture() }
            .thenCompose {
                cloudFoundryClient
                    .stopApplication(blueAppConfig.name)
                    .toFuture()
                    .thenApply {
                        logger.debug("Stopped application ${blueAppConfig.name}")
                        it
                    }
            }
    }

    private fun generateDeployApplicationFuture(appConfig: AppConfig): CompletableFuture<Void> {
        val pushAppAction = generatePushAppAction(appConfig)
        val setEnvActions = cloudFoundryClient.setApplicationEnvironment(appConfig)

        logger.debug("Pushing ${appConfig.name}.")
        val pushAppActionFuture = pushAppAction
            .toFuture()
            .thenApply {
                logger.debug("Pushed ${appConfig.name}, setting environment variables.")
                it
            }

        val pushAppWithEnvFuture = setEnvActions.fold(pushAppActionFuture) { acc, setEnvAction ->
                acc.thenCompose { setEnvAction.toFuture() }
            }

        val environmentSetFuture = pushAppWithEnvFuture.thenApply {
            logger.debug("Set environment variables for ${appConfig.name}, binding services.")
            it
        }


        val bindServiceActions: List<Mono<Void>> = generateBindServiceActions(appConfig)
        val bindServicesFuture = bindServiceActions
            .fold(environmentSetFuture) { acc, bindServiceAction ->
                acc.thenCompose { bindServiceAction.toFuture() }
            }

        val servicesBoundFuture = bindServicesFuture.thenApply {
            logger.debug("Bound services for ${appConfig.name}, starting application.")
            it
        }

        return servicesBoundFuture
            .thenCompose {
                cloudFoundryClient
                    .startApplication(appConfig.name)
                    .toFuture()
                    .thenApply {
                        logger.debug("Started ${appConfig.name}, mapping routes.")
                        it
                    }
            }
            .thenCompose {
                cloudFoundryClient
                    .mapRoute(appConfig)
                    .toFuture()
            }
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

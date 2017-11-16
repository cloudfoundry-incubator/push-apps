package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

class RetryError(val appConfig: AppConfig) : Throwable()

class AppDeployer(
    private val cloudFoundryClient: CloudFoundryClient,
    private val appConfigs: List<AppConfig>,
    private val availableServices: List<String>,
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger: Logger = LogManager.getLogger(AppDeployer::class.java)

    fun deployApps(): List<OperationResult> {
        val appNames = appConfigs.map(AppConfig::name)
        logger.info("Deploying applications: ${appNames.joinToString(", ")}")

        //FIXME: replace with thread safe queue, doesn't need to block
        val queue = LinkedBlockingQueue<AppConfig>()
        queue.addAll(appConfigs)

        val deploymentFunction = {appConfig: AppConfig ->
            deployApplication(appConfig)
        }

        val subscriber = AppDeploymentScheduler(
            maxInFlight = maxInFlight,
            appDeployer = deploymentFunction,
            appConfigQueue = queue,
            cloudFoundryClient = cloudFoundryClient,
            retries = retryCount
        )

        //FIXME: extract to injected thing that can be tested
        val flux = Flux.create<AppConfig>({ sink ->
            sink.onRequest({ n: Long ->
                if (queue.isEmpty()) sink.complete()

                val appConfigs = queue.take(n.toInt())
                appConfigs.forEach { appConfig ->
                    val wasRemoved = queue.remove(appConfig)
                    if (wasRemoved) sink.next(appConfig)
                }
            })
        })

        flux.subscribe(subscriber)

        val results = subscriber.results.get()
        logger.debug("Got results $results")
        return results
    }

    fun deployApplication(appConfig: AppConfig): CompletableFuture<OperationResult> {
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

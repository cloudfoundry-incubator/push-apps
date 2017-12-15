package org.cloudfoundry.pushapps

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.whenComplete
import java.util.concurrent.ConcurrentLinkedQueue

class AppDeployer(
    private val cloudFoundryClient: CloudFoundryClient,
    private val appConfigs: List<AppConfig>,
    private val availableServices: List<String>,
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger: Logger = LogManager.getLogger(AppDeployer::class.java)

    fun deployApps(): Flux<OperationResult> {
        val appNames = appConfigs.map(AppConfig::name)
        logger.info("Deploying applications: ${appNames.joinToString(", ")}")

        val queue = ConcurrentLinkedQueue<AppConfig>()
        queue.addAll(appConfigs)

        return Flux.create<OperationResult> { sink ->
            val subscriber = OperationScheduler<AppConfig>(
                maxInFlight = maxInFlight,
                sink = sink,
                operation = this::deployApplication,
                operationIdentifier = AppConfig::name,
                operationDescription = { appConfig: AppConfig -> "Push application ${appConfig.name}" },
                operationConfigQueue = queue,
                fetchLogs = { identifier -> cloudFoundryClient.fetchRecentLogsForAsync(identifier) }
            )

            val flux = createQueueBackedFlux(queue)
            flux.subscribe(subscriber)
        }
    }

    private fun deployApplication(appConfig: AppConfig): Flux<OperationResult> {
        return if (appConfig.blueGreenDeploy == true) {
            asyncBlueGreenDeployApplication(appConfig)
        } else {
            asyncDeployApplication(appConfig)
        }
    }

    private fun asyncBlueGreenDeployApplication(appConfig: AppConfig): Flux<OperationResult> {
        val applications = cloudFoundryClient
            .listApplications()
            .toIterable()
            .toList()

        val blueAppConfig = appConfig.copy(name = appConfig.name + "-blue")

        val deployBlueApplication = asyncDeployApplication(blueAppConfig)
            .transform(logAsyncOperation(logger, "Deploying blue application ${appConfig.name}"))

        val operations = mutableListOf<Publisher<OperationResult>>()

        if (applications.contains(appConfig.name)) {
            operations.add(doOperation(
                "Un-map current application routes ${appConfig.name}",
                cloudFoundryClient.unmapRoute(appConfig),
                appConfig
            ))
        }

        val deployGreenApplication = asyncDeployApplication(appConfig)
            .transform(logAsyncOperation(logger, "Deploy green application ${appConfig.name}"))

        val unmapBlueRoute = doOperation(
            "Un-map blue routes ${appConfig.name}",
            cloudFoundryClient.unmapRoute(blueAppConfig),
            appConfig
        )

        val stopBlueApplication = doOperation(
            "Stop blue application ${appConfig.name}",
            cloudFoundryClient.stopApplication(blueAppConfig.name),
            appConfig
        )

        operations.addAll(listOf(deployGreenApplication, unmapBlueRoute, stopBlueApplication))

        return Flux.concat(deployBlueApplication, *operations.toTypedArray())
    }


    private fun asyncDeployApplication(appConfig: AppConfig): Flux<OperationResult> {
        val pushAppAction = doOperation(
            "Push ${appConfig.name}",
            cloudFoundryClient.pushApplication(appConfig),
            appConfig
        )

        val setEnvActions = doOperation(
            "Set environment variables for ${appConfig.name}",
            cloudFoundryClient.setApplicationEnvironment(appConfig),
            appConfig
        )

        val bindServiceActions = doOperation(
            "Bind services for ${appConfig.name}",
            generateBindServiceActions(appConfig),
            appConfig
        )

        val startApplication = doOperation(
            "Start application ${appConfig.name}",
            cloudFoundryClient.startApplication(appConfig.name),
            appConfig
        )

        val mapRoute = doOperation(
            "Map routes for ${appConfig.name}",
            cloudFoundryClient.mapRoute(appConfig),
            appConfig
        )

        return Flux.concat(pushAppAction, setEnvActions, bindServiceActions, startApplication, mapRoute)
    }

    private fun doOperation(description: String, operation: Mono<Void>, appConfig: AppConfig): Mono<OperationResult> {
        return operation
            .transform(logAsyncOperation(logger, description))
            .then(Mono.just(
                OperationResult(
                    description = description,
                    didSucceed = true,
                    operationConfig = appConfig
                )))
    }

    private fun generateBindServiceActions(appConfig: AppConfig): Mono<Void> {
        var bindServiceActions: List<Mono<Void>> = emptyList()
        if (appConfig.serviceNames.isNotEmpty()) {
            val availableServicesToBeBound = appConfig.serviceNames.filter {
                availableServices.contains(it)
            }
            bindServiceActions = cloudFoundryClient.bindServicesToApplication(appConfig.name, availableServicesToBeBound)
        }
        return whenComplete(*bindServiceActions.toTypedArray())
    }
}

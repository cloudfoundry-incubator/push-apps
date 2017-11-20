package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.UnknownCloudFoundryException
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.CompletableFuture

class AppDeploymentScheduler(
    val maxInFlight: Int,
    val appDeployer: (AppConfig) -> Flux<OperationResult>,
    val appConfigQueue: Queue<AppConfig>,
    val cloudFoundryClient: CloudFoundryClient,
    val retries: Int = 0
) : Subscriber<AppConfig> {
    private val logger = LogManager.getLogger(AppDeploymentScheduler::class.java)
    private val deployments = mutableListOf<Flux<OperationResult>>()
    private val deploymentResults = mutableListOf<OperationResult>()

    private val retriesByApp = mutableMapOf<String, Int>()

    private lateinit var subscription: Subscription
    private var onNextAmount = 0

    val results = CompletableFuture<List<OperationResult>>()


    override fun onNext(appConfig: AppConfig) {
        val retryCount = retriesByApp.getOrDefault(appConfig.name, retries)
        retriesByApp[appConfig.name] = retryCount - 1

        val handleErrors: (Throwable) -> Mono<OperationResult> = { error ->
            when (error) {
                is UnknownCloudFoundryException -> {
                    logger.debug("Retrying deployment of ${appConfig.name} due to an UnknownCloudFoundryException with message ${error.message} and status code ${error.statusCode}.")
                    appConfigQueue.offer(appConfig)
                    Mono.empty()
                }
                else -> {
                    if (retryCount > 0) {
                        logger.debug("Retrying deployment of ${appConfig.name}, retry count at $retryCount.")
                        appConfigQueue.offer(appConfig)
                        Mono.empty()
                    } else {
                        val recentLogs = cloudFoundryClient
                            .fetchRecentLogsForAsync(appConfig.name)

                        Mono.just(OperationResult(appConfig.name, false, error, false, recentLogs))
                    }
                }
            }
        }

        val deployAppFlux = appDeployer(appConfig)
            .onErrorResume(handleErrors)

        deployments.add(deployAppFlux)

        onNextAmount++

        if (maxInFlightReached() || appConfigQueue.isEmpty()) {
            waitForCurrentDeployments()
            subscription.request(maxInFlight.toLong())
        }
    }

    private fun waitForCurrentDeployments() {
        val resultsList = deployments.flatMap { deployment ->
            deployment.toIterable().filterNotNull()
        }

        deploymentResults.addAll(resultsList)
        deployments.clear()
    }

    private fun maxInFlightReached() = (onNextAmount % maxInFlight) == 0

    override fun onComplete() {
        waitForCurrentDeployments()
        results.complete(deploymentResults)
    }

    override fun onSubscribe(s: Subscription) {
        subscription = s
        s.request(maxInFlight.toLong())
    }

    override fun onError(error: Throwable) {
        logger.debug("Got error ${error.message}, with cause ${error.cause} in ${this::class.java}.")
        //FIXME: test this
        deploymentResults.add(OperationResult(name = "Unknown", didSucceed = false, error = error))
    }
}

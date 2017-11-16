package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.UnknownCloudFoundryException
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

class AppDeploymentScheduler(
    val maxInFlight: Int,
    val appDeployer: (AppConfig) -> CompletableFuture<OperationResult>,
    val appConfigQueue: Queue<AppConfig>,
    val cloudFoundryClient: CloudFoundryClient,
    val retries: Int = 0
) : Subscriber<AppConfig> {
    private val logger = LogManager.getLogger(AppDeploymentScheduler::class.java)
    private val deployments = mutableListOf<CompletableFuture<OperationResult>>()
    private val deploymentResults = mutableListOf<OperationResult>()

    private val retriesByApp = mutableMapOf<String, Int>()

    private lateinit var subscription: Subscription
    private var onNextAmount = 0

    val results = CompletableFuture<List<OperationResult>>()


    override fun onNext(appConfig: AppConfig) {
        val retryCount = retriesByApp.getOrDefault(appConfig.name, retries)

        val future = appDeployer(appConfig).exceptionally { error ->
            val cause = error.cause
            when (cause) {
                is UnknownCloudFoundryException -> {
                    logger.debug("Retrying deployment of ${appConfig.name} due to an UnknownCloudFoundryException with message ${cause.message} and status code ${cause.statusCode}.")
                    throw RetryError(appConfig)
                }
                else -> {
                    if (retryCount > 0) {
                        logger.debug("Retrying deployment of ${appConfig.name}, retry count at $retryCount.")
                        throw RetryError(appConfig)
                    }

                    val recentLogs = cloudFoundryClient
                        .fetchRecentLogsForAsync(appConfig.name)

                    OperationResult(appConfig.name, false, cause, false, recentLogs)
                }
            }
        }

        retriesByApp[appConfig.name] = retryCount - 1

        deployments.add(future)

        onNextAmount++

        if (maxInFlightReached() || appConfigQueue.isEmpty()) {
            waitForCurrentDeployments()
            subscription.request(maxInFlight.toLong())
        }
    }

    private fun waitForCurrentDeployments() {
        val resultsList = deployments.mapNotNull { deployment ->
            try {
                deployment.get()
            } catch (error: CompletionException) {
                handleRetryErrors(error)
            } catch (error: ExecutionException) {
                handleRetryErrors(error)
            }
        }

        deploymentResults.addAll(resultsList)
        deployments.clear()
    }

    private fun handleRetryErrors(error: Exception): Nothing? {
        val cause = error.cause

        when (cause) {
            is RetryError -> {
                appConfigQueue.offer(cause.appConfig)
            }
        }

        return null
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

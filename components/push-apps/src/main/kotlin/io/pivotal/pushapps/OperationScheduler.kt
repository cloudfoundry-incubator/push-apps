package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.UnknownCloudFoundryException
import org.cloudfoundry.doppler.LogMessage
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.util.*
import java.util.concurrent.CompletableFuture

class OperationScheduler<T>(
    private val maxInFlight: Int,
    private val operation: (T) -> Publisher<OperationResult>,
    private val operationIdentifier: (T) -> String,
    private val operationDescription: (T) -> String,
    private val operationConfigQueue: Queue<T>,
    private val retries: Int = 0, private val fetchLogs: (String) -> Flux<LogMessage> = { _ -> Flux.fromIterable(emptyList()) }
) : Subscriber<T> {
    private val logger = LogManager.getLogger(OperationScheduler::class.java)
    private val pendingOperations = mutableListOf<Flux<OperationResult>>()
    private val operationResults = mutableListOf<OperationResult>()

    private val retriesByApp = mutableMapOf<String, Int>()

    private lateinit var subscription: Subscription
    private var onNextAmount = 0

    val results = CompletableFuture<List<OperationResult>>()

    override fun onNext(nextItem: T) {
        val identifier = operationIdentifier(nextItem)
        val retryCount = retriesByApp.getOrDefault(identifier, retries)
        retriesByApp[identifier] = retryCount - 1

        val handleErrors: (Throwable) -> Mono<OperationResult> = { error ->
            when (error) {
                is UnknownCloudFoundryException -> {
                    logger.debug("Retrying deployment of $identifier due to an UnknownCloudFoundryException with message ${error.message} and status code ${error.statusCode}.")
                    operationConfigQueue.offer(nextItem)
                    Mono.empty()
                }
                else -> {
                    if (retryCount > 0) {
                        logger.debug("Retrying deployment of $identifier, retry count at $retryCount.")
                        operationConfigQueue.offer(nextItem)
                        Mono.empty()
                    } else {
                        Mono.just(OperationResult(
                            name = operationDescription(nextItem),
                            didSucceed = false,
                            error = error,
                            optional = false,
                            recentLogs = fetchLogs(identifier)
                        ))
                    }
                }
            }
        }

        val operationFlux = operation(nextItem)
            .toFlux()
            .onErrorResume(handleErrors)

        pendingOperations.add(operationFlux)

        onNextAmount++

        if (maxInFlightReached() || operationConfigQueue.isEmpty()) {
            waitForCurrentDeployments()
            subscription.request(maxInFlight.toLong())
        }
    }

    private fun waitForCurrentDeployments() {
        val resultsList = pendingOperations.flatMap { deployment ->
            deployment.toIterable().filterNotNull()
        }

        operationResults.addAll(resultsList)
        pendingOperations.clear()
    }

    private fun maxInFlightReached() = (onNextAmount % maxInFlight) == 0

    override fun onComplete() {
        waitForCurrentDeployments()
        results.complete(operationResults)
    }

    override fun onSubscribe(s: Subscription) {
        subscription = s
        s.request(maxInFlight.toLong())
    }

    override fun onError(error: Throwable) {
        logger.debug("Got error ${error.message}, with cause ${error.cause} in ${this::class.java}.")
        //FIXME: test this
        operationResults.add(OperationResult(name = "Unknown", didSucceed = false, error = error))
    }
}

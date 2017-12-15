package org.cloudfoundry.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.doppler.LogMessage
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.util.*

typealias ErrorHandler = (Throwable) -> Mono<OperationResult>

class OperationScheduler<T : OperationConfig>(
    private val maxInFlight: Int,
    private val sink: FluxSink<OperationResult>,
    private val operation: (T) -> Publisher<OperationResult>,
    private val operationIdentifier: (T) -> String,
    private val operationDescription: (T) -> String,
    private val operationConfigQueue: Queue<T>,
    private val fetchLogs: (String) -> Flux<LogMessage> = { _ -> Flux.fromIterable(emptyList()) }
) : Subscriber<T> {
    private val logger = LogManager.getLogger(OperationScheduler::class.java)
    private val pendingOperations = mutableListOf<Flux<OperationResult>>()

    private lateinit var subscription: Subscription
    private var onNextAmount = 0

    override fun onNext(nextItem: T) {
        val identifier = operationIdentifier(nextItem)

        val handleErrors: (T) -> ErrorHandler = { config: T ->
            { error ->
                val operationResult = OperationResult(
                    description = operationDescription(nextItem),
                    operationConfig = config,
                    didSucceed = false,
                    error = error,
                    recentLogs = fetchLogs(identifier)
                )

                Mono.just(operationResult)
            }
        }

        val operationFlux = operation(nextItem)
            .toFlux()
            .onErrorResume(handleErrors(nextItem))

        pendingOperations.add(operationFlux)

        onNextAmount++

        if (maxInFlightReached() || operationConfigQueue.isEmpty()) {
            waitForCurrentDeployments()
            subscription.request(maxInFlight.toLong())
        }
    }

    private fun waitForCurrentDeployments() {
        pendingOperations.flatMap { deployment ->
            deployment.toIterable().filterNotNull()
        }.map(sink::next)

        pendingOperations.clear()
    }

    private fun maxInFlightReached() = (onNextAmount % maxInFlight) == 0

    override fun onComplete() {
        waitForCurrentDeployments()
        sink.complete()
    }

    override fun onSubscribe(s: Subscription) {
        subscription = s
        s.request(maxInFlight.toLong())
    }

    override fun onError(error: Throwable) {
        logger.debug("Got error ${error.message}, with cause ${error.cause} in ${this::class.java}.")
        throw error
    }
}
package org.cloudfoundry.tools.pushapps

import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.tools.pushapps.config.OperationConfig
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers

fun <T : OperationConfig> scheduleOperations(
    configs: List<T>,
    maxInFlight: Int,
    operation: (T) -> Publisher<OperationResult>,
    operationIdentifier: (T) -> String,
    operationDescription: (T) -> String,
    fetchLogs: (String) -> Flux<LogMessage> = { _ -> Flux.fromIterable(emptyList()) }
): Flux<OperationResult> {
    return Flux
        .fromIterable(configs)
        .window(maxInFlight)
        .flatMap { configWindow: Flux<T> ->
            configWindow
                .parallel()
                .runOn(Schedulers.elastic()) //TODO: switch this to parallel and fix all the things that breaks
                .flatMap { config ->
                    val identifier = operationIdentifier(config)
                    operation(config)
                        .toFlux()
                        .onErrorResume { error ->
                            val operationResult = OperationResult(
                                description = operationDescription(config),
                                operationConfig = config,
                                didSucceed = false,
                                error = error,
                                recentLogs = fetchLogs(identifier)
                            )

                            Mono.just(operationResult)
                        }
                }
        }
}

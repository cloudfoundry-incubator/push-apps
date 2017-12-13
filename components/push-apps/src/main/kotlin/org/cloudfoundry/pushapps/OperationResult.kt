package org.cloudfoundry.pushapps

import org.cloudfoundry.doppler.LogMessage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class OperationResult(
    val description: String,
    val operationConfig: OperationConfig,
    val didSucceed: Boolean,
    val error: Throwable? = null,
    val recentLogs: Flux<LogMessage> = Flux.fromIterable(emptyList())
)

inline fun <reified T> convertToOperationResult(description: String, config: OperationConfig): (T) -> Mono<OperationResult> {
    return {
        val operationResult = OperationResult(
            description = description,
            didSucceed = true,
            operationConfig = config
        )

        Mono.just(operationResult)
    }
}

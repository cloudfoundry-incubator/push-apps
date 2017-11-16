package io.pivotal.pushapps

import org.cloudfoundry.doppler.LogMessage
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

data class OperationResult(
    val name: String,
    val didSucceed: Boolean,
    val error: Throwable? = null,
    val optional: Boolean = false,
    val recentLogs: Flux<LogMessage> = Flux.fromIterable(emptyList())
)

fun getOperationResult(operation: CompletableFuture<Void>, name: String, optional: Boolean): CompletableFuture<OperationResult> {
    return operation
        .thenApply {
            OperationResult(
                name = name,
                didSucceed = true
            )
        }
        .exceptionally { error ->
            OperationResult(
                name = name,
                didSucceed = false,
                error = error,
                optional = optional
            )
        }
}

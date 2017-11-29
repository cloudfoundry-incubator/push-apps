package io.pivotal.pushapps

import org.apache.logging.log4j.Logger
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

fun <T> logAsyncOperation(logger: Logger, operation: String): (Publisher<T>) -> Publisher<T> {
    val printSubscriptionMessage: (Subscription) -> Unit = {
        logger.debug("$operation: STARTED")
    }

    val printSuccessMessage = {
        logger.info("$operation: SUCCESS")
    }

    val printErrorMessage: (Throwable) -> Unit = {
        logger.error("$operation: ERROR")
    }

    return {
        when (it) {
            is Mono -> it
                .doOnSubscribe(printSubscriptionMessage)
                .doOnSuccess({ _: T -> printSuccessMessage() })
                .doOnError(printErrorMessage)
            is Flux -> it
                .doOnSubscribe(printSubscriptionMessage)
                .doOnComplete(printSuccessMessage)
                .doOnError(printErrorMessage)
            else -> it
        }
    }
}

package io.pivotal.pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.ConcurrentLinkedQueue

//FIXME: this flux should be tested
fun <T> createQueueBackedFlux(queue: ConcurrentLinkedQueue<T>): Flux<T> {
    return Flux.create<T>({ sink ->
        sink.onRequest({ n: Long ->
            if (queue.isEmpty()) sink.complete()

            (1..n).forEach {
                val next = queue.poll()
                if (next !== null) {
                    sink.next(next)
                }
            }
        })
    })
}

package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.pushapps.AppConfig
import org.cloudfoundry.pushapps.OperationResult
import org.cloudfoundry.pushapps.OperationScheduler
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class OperationSchedulerTest : Spek({
    val appConfig = mock<AppConfig>()
    val operationResult = OperationResult(
        description = "foo bar",
        didSucceed = true,
        operationConfig = appConfig
    )
    val flux = Flux.just(operationResult)
    val appDeployer = { _: AppConfig -> flux }
    val appConfigIdentifier = { config: AppConfig -> config.name }
    val subscription = mock<Subscription>()

    beforeEachTest {
        whenever(appConfig.name).thenReturn("foo bar")
    }

    describe("#onSubscribe") {
        it("requests maxInFlight number of items from subscription") {
            val sink = mock<FluxSink<OperationResult>>()

            val scheduler = OperationScheduler(
                maxInFlight = 4,
                sink = sink,
                operation = appDeployer,
                operationIdentifier = appConfigIdentifier,
                operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                operationConfigQueue = mock<Queue<AppConfig>>()
            )
            scheduler.onSubscribe(subscription)

            verify(subscription).request(4.toLong())
        }
    }

    describe("#onComplete") {
        it("waits on all outstanding application deployments, and returns a future that completes with the deployment results") {
            val sink = mock<FluxSink<OperationResult>>()
            val queue = ConcurrentLinkedQueue<AppConfig>()
            queue.add(appConfig)

            val scheduler = OperationScheduler(
                maxInFlight = 4,
                sink = sink,
                operation = appDeployer,
                operationIdentifier = appConfigIdentifier,
                operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                operationConfigQueue = queue
            )
            scheduler.onSubscribe(subscription)
            scheduler.onNext(queue.poll())

            verify(sink).next(operationResult)
            verifyNoMoreInteractions(sink)

            scheduler.onComplete()

            verify(sink).complete()
        }
    }

    describe("#onNext") {
        it("calls the operation to get a deployment future") {
            val sink = mock<FluxSink<OperationResult>>()
            var appDeployerWasCalled = false
            val deployer = { _: AppConfig ->
                appDeployerWasCalled = true
                flux
            }

            val queue = ConcurrentLinkedQueue<AppConfig>()
            queue.add(appConfig)

            val scheduler = OperationScheduler(
                maxInFlight = 4,
                sink = sink,
                operation = deployer,
                operationIdentifier = appConfigIdentifier,
                operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                operationConfigQueue = mock<Queue<AppConfig>>()
            )
            scheduler.onSubscribe(subscription)
            scheduler.onNext(queue.poll())
            assertThat(appDeployerWasCalled).isTrue()
        }

        it("requests more items from the subscription in intervals of max in flight") {
            val sink = mock<FluxSink<OperationResult>>()
            val sub = mock<Subscription>()
            val queue = ConcurrentLinkedQueue<AppConfig>()
            queue.addAll(listOf(appConfig, appConfig, appConfig, appConfig))

            val scheduler = OperationScheduler(
                maxInFlight = 2,
                sink = sink,
                operation = appDeployer,
                operationIdentifier = appConfigIdentifier,
                operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                operationConfigQueue = queue
            )
            scheduler.onSubscribe(sub)

            scheduler.onNext(queue.poll())
            scheduler.onNext(queue.poll())
            scheduler.onNext(queue.poll())
            scheduler.onNext(queue.poll())

            verify(sub, times(3)).request(2.toLong())
        }

        context("when operation completes exceptionally with any other exception") {
            it("fetch logs for the failed app if log fetcher is specified") {
                val sink = mock<FluxSink<OperationResult>>()
                val exceptionalFlux = Flux.error<OperationResult>(RuntimeException())
                val deployer = { _: AppConfig -> exceptionalFlux }

                val logMessage = mock<LogMessage>()

                val scheduler = OperationScheduler(
                    maxInFlight = 1,
                    sink = sink,
                    operation = deployer,
                    operationIdentifier = appConfigIdentifier,
                    operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                    operationConfigQueue = mock<Queue<AppConfig>>(),
                    fetchLogs = { Flux.fromIterable(listOf(logMessage)) }
                )
                scheduler.onSubscribe(subscription)

                scheduler.onNext(appConfig)
                scheduler.onComplete()

                verify(sink).next(argForWhich {
                    val logs = recentLogs.toIterable().toList()
                    logs.size == 1 && logs[0] == logMessage
                })
            }
        }
    }
})

package unit

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.UnknownCloudFoundryException
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

        context("when operation completes exceptionally with an UnknownCloudFoundryException") {
            it("requeue the failed appConfig") {
                val sink = mock<FluxSink<OperationResult>>()
                val exceptionalFlux = Flux.error<OperationResult>(UnknownCloudFoundryException(502))
                val deployer = { _: AppConfig -> exceptionalFlux }

                val queue = ConcurrentLinkedQueue<AppConfig>()
                queue.add(appConfig)

                val scheduler = OperationScheduler(
                    maxInFlight = 1,
                    sink = sink,
                    operation = deployer,
                    operationIdentifier = appConfigIdentifier,
                    operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                    operationConfigQueue = queue
                )
                scheduler.onSubscribe(subscription)
                scheduler.onNext(queue.poll())

                assertThat(queue).hasSize(1)
                assertThat(queue.poll()).isEqualTo(appConfig)
            }

            it("only retries failed deployments, and does not lose successful ones") {
                val sink = mock<FluxSink<OperationResult>>()
                var deployerCalls = 0

                val fooBarConfig = mock<AppConfig>()
                val helloWorldConfig = mock<AppConfig>()

                whenever(fooBarConfig.name).thenReturn("foo bar")
                whenever(helloWorldConfig.name).thenReturn("hello world")

                val firstFlux = Flux.just<OperationResult>(OperationResult(
                    description = "deploy foo bar",
                    didSucceed = true,
                    operationConfig = fooBarConfig
                ))
                val exceptionalFlux = Flux.error<OperationResult>(UnknownCloudFoundryException(502))
                val secondFlux = Flux.just<OperationResult>(OperationResult(
                    description = "deploy hello world",
                    didSucceed = true,
                    operationConfig = helloWorldConfig
                ))
                val fluxes = listOf(firstFlux, exceptionalFlux, secondFlux)

                val deployer = { _: AppConfig -> fluxes[deployerCalls++] }

                val queue = ConcurrentLinkedQueue<AppConfig>()
                queue.addAll(listOf(fooBarConfig, helloWorldConfig))

                val scheduler = OperationScheduler(
                    maxInFlight = 2,
                    sink = sink,
                    operation = deployer,
                    operationIdentifier = appConfigIdentifier,
                    operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                    operationConfigQueue = queue,
                    retries = 1
                )
                scheduler.onSubscribe(subscription)

                scheduler.onNext(queue.poll())
                scheduler.onNext(queue.poll())
                scheduler.onNext(queue.poll())
                scheduler.onComplete()

                verify(sink).next(argForWhich {
                    description == "deploy foo bar" && didSucceed
                })
                verify(sink).next(argForWhich {
                    description == "deploy hello world" && didSucceed
                })
                verify(sink).complete()

                verifyNoMoreInteractions(sink)
            }
        }

        context("when operation completes exceptionally with any other exception") {
            context("when number of failures is below the retry count") {
                it("requeue the failed appConfig if number of failures is below the retry count") {
                    val sink = mock<FluxSink<OperationResult>>()
                    val exceptionalFlux = Flux.error<OperationResult>(RuntimeException())
                    val deployer = { _: AppConfig -> exceptionalFlux }

                    val queue = ConcurrentLinkedQueue<AppConfig>()
                    queue.add(appConfig)

                    val scheduler = OperationScheduler(
                        maxInFlight = 1,
                        sink = sink,
                        operation = deployer,
                        operationIdentifier = appConfigIdentifier,
                        operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                        operationConfigQueue = queue,
                        retries = 1
                    )

                    scheduler.onSubscribe(subscription)
                    scheduler.onNext(queue.poll())
                    assertThat(queue).hasSize(1)

                    scheduler.onNext(queue.poll())
                    assertThat(queue).isEmpty()
                }

                it("only retries failed deployments, and does not lose successful ones") {
                    val sink = mock<FluxSink<OperationResult>>()
                    var deployerCalls = 0

                    val fooBarConfig = mock<AppConfig>()
                    val helloWorldConfig = mock<AppConfig>()

                    whenever(fooBarConfig.name).thenReturn("foo bar")
                    whenever(helloWorldConfig.name).thenReturn("hello world")

                    val firstFlux = Flux.just<OperationResult>(OperationResult(
                        description = "deploy foo bar",
                        didSucceed = true,
                        operationConfig = fooBarConfig
                    ))
                    val exceptionalFlux = Flux.error<OperationResult>(RuntimeException())
                    val secondFlux = Flux.just<OperationResult>(OperationResult(
                        description = "deploy hello world",
                        didSucceed = true,
                        operationConfig = helloWorldConfig
                    ))
                    val fluxes = listOf(firstFlux, exceptionalFlux, secondFlux)

                    val deployer = { _: AppConfig -> fluxes[deployerCalls++] }

                    val queue = ConcurrentLinkedQueue<AppConfig>()
                    queue.addAll(listOf(fooBarConfig, helloWorldConfig))

                    val scheduler = OperationScheduler(
                        maxInFlight = 2,
                        sink = sink,
                        operation = deployer,
                        operationIdentifier = appConfigIdentifier,
                        operationDescription = { appConfig -> "Push application ${appConfig.name}" },
                        operationConfigQueue = queue,
                        retries = 1
                    )

                    scheduler.onSubscribe(subscription)

                    scheduler.onNext(queue.poll())
                    scheduler.onNext(queue.poll())
                    scheduler.onNext(queue.poll())
                    scheduler.onComplete()

                    verify(sink).next(argForWhich {
                        description == "deploy foo bar" && didSucceed
                    })

                    verify(sink).next(argForWhich {
                        description == "deploy hello world" && didSucceed
                    })

                    verify(sink).complete()
                }
            }

            context("when number of failures is above the retry count") {
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
                        retries = 0,
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
    }
})

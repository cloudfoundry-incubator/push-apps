package unit

import com.nhaarman.mockito_kotlin.*
import io.pivotal.pushapps.AppConfig
import io.pivotal.pushapps.OperationScheduler
import io.pivotal.pushapps.CloudFoundryClient
import io.pivotal.pushapps.OperationResult
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.UnknownCloudFoundryException
import org.cloudfoundry.doppler.LogMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue

class OperationSchedulerTest : Spek({
    val appConfig = mock<AppConfig>()
    val operationResult = OperationResult("foo bar", true)
    val flux = Flux.just(operationResult)
    val appDeployer = { _: AppConfig -> flux }
    val appConfigIdentifier = { config: AppConfig -> config.name }
    val subscription = mock<Subscription>()

    beforeEachTest {
        whenever(appConfig.name).thenReturn("foo bar")
    }

    describe("#onSubscribe") {
        it("requests maxInFlight number of items from subscription") {
            val scheduler = OperationScheduler(
                maxInFlight = 4,
                operation = appDeployer,
                operationIdentifier = appConfigIdentifier,
                operationConfigQueue = mock<Queue<AppConfig>>(),
                cloudFoundryClient = mock<CloudFoundryClient>()
            )
            scheduler.onSubscribe(subscription)

            verify(subscription).request(4.toLong())
        }
    }

    describe("#onComplete") {
        it("waits on all outstanding application deployments, and returns a future that completes with the deployment results") {
            val scheduler = OperationScheduler(
                maxInFlight = 4,
                operation = appDeployer,
                operationIdentifier = appConfigIdentifier,
                operationConfigQueue = mock<Queue<AppConfig>>(),
                cloudFoundryClient = mock<CloudFoundryClient>()
            )
            scheduler.onSubscribe(subscription)

            scheduler.onNext(appConfig)

            val resultsFuture = scheduler.results
            assertThat(resultsFuture.isDone).isFalse()

            scheduler.onComplete()

            assertThat(resultsFuture.isDone).isTrue()

            val results = resultsFuture.get()

            assertThat(results).hasSize(1)
            assertThat(results[0]).isEqualTo(operationResult)
        }
    }

    describe("#onNext") {
        it("calls the operation to get a deployment future") {
            var appDeployerWasCalled = false
            val deployer = { _: AppConfig ->
                appDeployerWasCalled = true
                flux
            }

            val scheduler = OperationScheduler(
                maxInFlight = 4,
                operation = deployer,
                operationIdentifier = appConfigIdentifier,
                operationConfigQueue = mock<Queue<AppConfig>>(),
                cloudFoundryClient = mock<CloudFoundryClient>()
            )
            scheduler.onSubscribe(subscription)

            scheduler.onNext(mock<AppConfig>())
            assertThat(appDeployerWasCalled).isTrue()
        }

        it("requests more items from the subscription in intervals of max in flight") {
            val sub = mock<Subscription>()
            val queue = mock<BlockingQueue<AppConfig>>()
            whenever(queue.isEmpty()).thenReturn(false)

            val scheduler = OperationScheduler(
                maxInFlight = 2,
                operation = appDeployer,
                operationIdentifier = appConfigIdentifier,
                operationConfigQueue = queue,
                cloudFoundryClient = mock<CloudFoundryClient>()
            )
            scheduler.onSubscribe(sub)

            scheduler.onNext(appConfig)
            scheduler.onNext(appConfig)
            scheduler.onNext(appConfig)

            verify(sub, times(2)).request(2.toLong())
        }

        context("when operation completes exceptionally with an UnknownCloudFoundryException") {
            it("requeue the failed appConfig") {
                val exceptionalFlux = Flux.error<OperationResult>(UnknownCloudFoundryException(502))
                val deployer = { _: AppConfig -> exceptionalFlux }

                val queue = ConcurrentLinkedQueue<AppConfig>()

                val scheduler = OperationScheduler(
                    maxInFlight = 1,
                    operation = deployer,
                    operationIdentifier = appConfigIdentifier,
                    operationConfigQueue = queue,
                    cloudFoundryClient = mock<CloudFoundryClient>()
                )
                scheduler.onSubscribe(subscription)

                scheduler.onNext(appConfig)

                assertThat(queue).hasSize(1)
                assertThat(queue.poll()).isEqualTo(appConfig)
            }

            it("only retries failed deployments, and does not lose successful ones") {
                var deployerCalls = 0

                val firstFlux = Flux.just<OperationResult>(OperationResult("foo bar", true))
                val secondFlux = Flux.just<OperationResult>(OperationResult("hello world", true))
                val exceptionalFlux = Flux.error<OperationResult>(UnknownCloudFoundryException(502))
                val fluxes = listOf(firstFlux, exceptionalFlux, secondFlux)

                val deployer = { _: AppConfig -> fluxes[deployerCalls++] }

                val queue = mock<BlockingQueue<AppConfig>>()

                val scheduler = OperationScheduler(
                    maxInFlight = 2,
                    operation = deployer,
                    operationIdentifier = appConfigIdentifier,
                    operationConfigQueue = queue,
                    cloudFoundryClient = mock<CloudFoundryClient>(),
                    retries = 1
                )
                scheduler.onSubscribe(subscription)

                val fooBarConfig = mock<AppConfig>()
                val helloWorldConfig = mock<AppConfig>()

                whenever(fooBarConfig.name).thenReturn("foo bar")
                whenever(helloWorldConfig.name).thenReturn("hello world")

                scheduler.onNext(fooBarConfig)
                scheduler.onNext(helloWorldConfig)
                scheduler.onNext(helloWorldConfig)

                scheduler.onComplete()

                val results = scheduler.results.get()

                assertThat(results).hasSize(2)
                assertThat(results[0].name).isEqualTo("foo bar")
                assertThat(results[0].didSucceed).isTrue()
                assertThat(results[1].name).isEqualTo("hello world")
                assertThat(results[1].didSucceed).isTrue()
            }
        }

        context("when operation completes exceptionally with any other exception") {
            context("when number of failures is below the retry count") {
                it("requeue the failed appConfig if number of failures is below the retry count") {
                    val exceptionalFlux = Flux.error<OperationResult>(RuntimeException())
                    val deployer = { _: AppConfig -> exceptionalFlux }

                    val queue = mock<BlockingQueue<AppConfig>>()

                    val cloudFoundryClient = mock<CloudFoundryClient>()
                    val scheduler = OperationScheduler(
                        maxInFlight = 1,
                        operation = deployer,
                        operationIdentifier = appConfigIdentifier,
                        operationConfigQueue = queue,
                        cloudFoundryClient = cloudFoundryClient,
                        retries = 1
                    )

                    whenever(cloudFoundryClient.fetchRecentLogsForAsync(any()))
                        .thenReturn(Flux.just(mock<LogMessage>()))

                    scheduler.onSubscribe(subscription)
                    scheduler.onNext(appConfig)

                    verify(queue).offer(appConfig)

                    scheduler.onNext(appConfig)

                    verifyNoMoreInteractions(queue)
                }

                it("only retries failed deployments, and does not lose successful ones") {
                    var deployerCalls = 0

                    val firstFlux = Flux.just<OperationResult>(OperationResult("foo bar", true))
                    val secondFlux = Flux.just<OperationResult>(OperationResult("hello world", true))
                    val exceptionalFlux = Flux.error<OperationResult>(RuntimeException())
                    val fluxes = listOf(firstFlux, exceptionalFlux, secondFlux)

                    val deployer = { _: AppConfig -> fluxes[deployerCalls++] }

                    val queue = mock<BlockingQueue<AppConfig>>()

                    val cloudFoundryClient = mock<CloudFoundryClient>()
                    val scheduler = OperationScheduler(
                        maxInFlight = 2,
                        operation = deployer,
                        operationIdentifier = appConfigIdentifier,
                        operationConfigQueue = queue,
                        cloudFoundryClient = cloudFoundryClient,
                        retries = 1
                    )
                    val fooBarConfig = mock<AppConfig>()
                    val helloWorldConfig = mock<AppConfig>()

                    whenever(fooBarConfig.name).thenReturn("foo bar")
                    whenever(helloWorldConfig.name).thenReturn("hello world")
                    whenever(cloudFoundryClient.fetchRecentLogsForAsync(any()))
                        .thenReturn(Flux.just(mock<LogMessage>()))

                    scheduler.onSubscribe(subscription)

                    scheduler.onNext(fooBarConfig)
                    scheduler.onNext(helloWorldConfig)
                    scheduler.onNext(helloWorldConfig)

                    scheduler.onComplete()

                    val results = scheduler.results.get()

                    assertThat(results).hasSize(2)
                    assertThat(results[0].name).isEqualTo("foo bar")
                    assertThat(results[0].didSucceed).isTrue()
                    assertThat(results[1].name).isEqualTo("hello world")
                    assertThat(results[1].didSucceed).isTrue()
                }
            }

            context("when number of failures is above the retry count") {
                it("fetch logs for the failed app") {
                    val exceptionalFlux = Flux.error<OperationResult>(RuntimeException())
                    val deployer = { _: AppConfig -> exceptionalFlux }

                    val cloudFoundryClient = mock<CloudFoundryClient>()
                    val logMessage = mock<LogMessage>()

                    whenever(cloudFoundryClient.fetchRecentLogsForAsync(any())).thenReturn(Flux.fromIterable(listOf(logMessage)))

                    val scheduler = OperationScheduler(
                        maxInFlight = 1,
                        operation = deployer,
                        operationIdentifier = appConfigIdentifier,
                        operationConfigQueue = mock<Queue<AppConfig>>(),
                        cloudFoundryClient = cloudFoundryClient,
                        retries = 0
                    )
                    scheduler.onSubscribe(subscription)

                    scheduler.onNext(appConfig)
                    scheduler.onComplete()

                    val results = scheduler.results.get()

                    assertThat(results).hasSize(1)

                    val recentLogs = results[0].recentLogs.toIterable().toList()
                    assertThat(recentLogs).hasSize(1)
                    assertThat(recentLogs[0]).isEqualTo(logMessage)
                }
            }
        }
    }
})

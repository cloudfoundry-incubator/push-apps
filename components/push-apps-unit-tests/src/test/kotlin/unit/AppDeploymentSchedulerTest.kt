package unit

import com.nhaarman.mockito_kotlin.*
import io.pivotal.pushapps.AppConfig
import io.pivotal.pushapps.AppDeploymentScheduler
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentLinkedQueue

class AppDeploymentSchedulerTest : Spek({
    val appConfig = mock<AppConfig>()
    val future = CompletableFuture<OperationResult>()
    val appDeployer = { _: AppConfig ->
        future
    }
    val operationResult = OperationResult("foo bar", true)
    val subscription = mock<Subscription>()

    beforeEachTest {
        future.complete(operationResult)
        whenever(appConfig.name).thenReturn("foo bar")
    }

    describe("#onSubscribe") {
        it("requests maxInFlight number of items from subscription") {
            val scheduler = AppDeploymentScheduler(
                maxInFlight = 4,
                appDeployer = appDeployer,
                appConfigQueue = mock<Queue<AppConfig>>(),
                cloudFoundryClient = mock<CloudFoundryClient>()
            )
            scheduler.onSubscribe(subscription)

            verify(subscription).request(4.toLong())
        }
    }

    describe("#onComplete") {
        it("waits on all outstanding application deployments, and returns a future that completes with the deployment results") {
            val scheduler = AppDeploymentScheduler(
                maxInFlight = 4,
                appDeployer = appDeployer,
                appConfigQueue = mock<Queue<AppConfig>>(),
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
        it("calls the appDeployer to get a deployment future") {
            var appDeployerWasCalled = false
            val deployer = { _: AppConfig ->
                appDeployerWasCalled = true
                future
            }

            val scheduler = AppDeploymentScheduler(
                maxInFlight = 4,
                appDeployer = deployer,
                appConfigQueue = mock<Queue<AppConfig>>(),
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

            val scheduler = AppDeploymentScheduler(
                maxInFlight = 2,
                appDeployer = appDeployer,
                appConfigQueue = queue,
                cloudFoundryClient = mock<CloudFoundryClient>()
            )
            scheduler.onSubscribe(sub)

            scheduler.onNext(appConfig)
            scheduler.onNext(appConfig)
            scheduler.onNext(appConfig)

            verify(sub, times(2)).request(2.toLong())
        }

        context("when appDeployer completes exceptionally with an UnknownCloudFoundryException") {
            it("requeue the failed appConfig") {
                val exceptionalFuture = CompletableFuture<OperationResult>()
                val deployer = { _: AppConfig ->
                    exceptionalFuture
                }

                exceptionalFuture.completeExceptionally(CompletionException(UnknownCloudFoundryException(502)))
                val queue = ConcurrentLinkedQueue<AppConfig>()

                val scheduler = AppDeploymentScheduler(
                    maxInFlight = 1,
                    appDeployer = deployer,
                    appConfigQueue = queue,
                    cloudFoundryClient = mock<CloudFoundryClient>()
                )
                scheduler.onSubscribe(subscription)

                scheduler.onNext(appConfig)

                assertThat(queue).hasSize(1)
                assertThat(queue.poll()).isEqualTo(appConfig)
            }

            it("only retries failed deployments, and does not lose successful ones") {
                var deployerCalls = 0

                val firstFuture = CompletableFuture<OperationResult>()
                val secondFuture = CompletableFuture<OperationResult>()
                val exceptionalFuture = CompletableFuture<OperationResult>()
                val futures = listOf(firstFuture, exceptionalFuture, secondFuture)

                val deployer = { _: AppConfig ->
                    futures[deployerCalls++]
                }

                firstFuture.complete(OperationResult("foo bar", true))
                exceptionalFuture.completeExceptionally(CompletionException(UnknownCloudFoundryException(502)))
                secondFuture.complete(OperationResult("hello world", true))

                val queue = mock<BlockingQueue<AppConfig>>()

                val scheduler = AppDeploymentScheduler(
                    maxInFlight = 2,
                    appDeployer = deployer,
                    appConfigQueue = queue,
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

        context("when appDeployer completes exceptionally with any other exception") {
            context("when number of failures is below the retry count") {
                it("requeue the failed appConfig if number of failures is below the retry count") {
                    val exceptionalFuture = CompletableFuture<OperationResult>()
                    val deployer = { _: AppConfig ->
                        exceptionalFuture
                    }

                    exceptionalFuture.completeExceptionally(CompletionException(RuntimeException()))
                    val queue = mock<BlockingQueue<AppConfig>>()

                    val scheduler = AppDeploymentScheduler(
                        maxInFlight = 1,
                        appDeployer = deployer,
                        appConfigQueue = queue,
                        cloudFoundryClient = mock<CloudFoundryClient>(),
                        retries = 1
                    )
                    scheduler.onSubscribe(subscription)

                    scheduler.onNext(appConfig)

                    verify(queue).offer(appConfig)

                    scheduler.onNext(appConfig)

                    verifyNoMoreInteractions(queue)
                }

                it("only retries failed deployments, and does not lose successful ones") {
                    var deployerCalls = 0

                    val firstFuture = CompletableFuture<OperationResult>()
                    val secondFuture = CompletableFuture<OperationResult>()
                    val exceptionalFuture = CompletableFuture<OperationResult>()
                    val futures = listOf(firstFuture, exceptionalFuture, secondFuture)

                    val deployer = { _: AppConfig ->
                        futures[deployerCalls++]
                    }

                    firstFuture.complete(OperationResult("foo bar", true))
                    exceptionalFuture.completeExceptionally(CompletionException(RuntimeException()))
                    secondFuture.complete(OperationResult("hello world", true))

                    val queue = mock<BlockingQueue<AppConfig>>()

                    val scheduler = AppDeploymentScheduler(
                        maxInFlight = 2,
                        appDeployer = deployer,
                        appConfigQueue = queue,
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

            context("when number of failures is above the retry count") {
                it("fetch logs for the failed app") {
                    val exceptionalFuture = CompletableFuture<OperationResult>()
                    val deployer = { _: AppConfig ->
                        exceptionalFuture
                    }

                    exceptionalFuture.completeExceptionally(CompletionException(RuntimeException()))
                    val cloudFoundryClient = mock<CloudFoundryClient>()
                    val logMessage = mock<LogMessage>()

                    whenever(cloudFoundryClient.fetchRecentLogsForAsync(any())).thenReturn(Flux.fromIterable(listOf(logMessage)))

                    val scheduler = AppDeploymentScheduler(
                        maxInFlight = 1,
                        appDeployer = deployer,
                        appConfigQueue = mock<Queue<AppConfig>>(),
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

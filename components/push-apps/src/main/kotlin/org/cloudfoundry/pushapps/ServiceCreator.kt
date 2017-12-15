package org.cloudfoundry.pushapps

import org.apache.logging.log4j.LogManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentLinkedQueue

class ServiceCreator(
    private val serviceConfigs: List<ServiceConfig>,
    private val cloudFoundryClient: CloudFoundryClient,
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger = LogManager.getLogger(ServiceCreator::class.java)

    fun createServices(): Flux<OperationResult> {
        val serviceNames = serviceConfigs.map(ServiceConfig::name)
        logger.info("Creating services: ${serviceNames.joinToString(", ")}.")

        val queue = ConcurrentLinkedQueue<ServiceConfig>()
        queue.addAll(filterExistingServices())

        return Flux.create<OperationResult> { sink ->
            val subscriber = OperationScheduler<ServiceConfig>(
                maxInFlight = maxInFlight,
                sink = sink,
                operation = this::createService,
                operationIdentifier = ServiceConfig::name,
                operationDescription = this::createServiceDescription,
                operationConfigQueue = queue
            )

            val flux = createQueueBackedFlux(queue)
            flux.subscribe(subscriber)
        }
    }

    private fun filterExistingServices(): List<ServiceConfig> {
        val existingServiceNames = cloudFoundryClient
            .listServices()
            .toIterable()
            .toList()

        return serviceConfigs.filter { serviceConfig ->
            !existingServiceNames.contains(serviceConfig.name)
        }
    }

    private fun createServiceDescription(serviceConfig: ServiceConfig) = "Create service ${serviceConfig.name}"

    private fun createService(serviceConfig: ServiceConfig): Mono<OperationResult> {
        val description = createServiceDescription(serviceConfig)
        val operationResult = OperationResult(
            description = description,
            operationConfig = serviceConfig,
            didSucceed = true
        )

        return cloudFoundryClient
            .createService(serviceConfig)
            .transform(logAsyncOperation(logger, description))
            .then(Mono.just(operationResult))
    }
}

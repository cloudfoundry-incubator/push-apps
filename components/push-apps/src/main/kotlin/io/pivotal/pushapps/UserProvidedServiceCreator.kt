package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentLinkedQueue

class UserProvidedServiceCreator(
    private val cloudFoundryClient: CloudFoundryClient,
    private val serviceConfigs: List<UserProvidedServiceConfig>,
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger = LogManager.getLogger(UserProvidedServiceCreator::class.java)

    fun createOrUpdateServices(): List<OperationResult> {
        val serviceNames = serviceConfigs.map(UserProvidedServiceConfig::name)
        logger.info("Creating user provided services: ${serviceNames.joinToString(", ")}.")

        val queue = ConcurrentLinkedQueue<UserProvidedServiceConfig>()
        queue.addAll(serviceConfigs)

        val existingServiceNames = cloudFoundryClient.listServices()

        val createServiceOperation = { serviceConfig: UserProvidedServiceConfig ->
            createUserProvidedService(existingServiceNames, serviceConfig)
        }

        val subscriber = OperationScheduler<UserProvidedServiceConfig>(
            maxInFlight = maxInFlight,
            operation = createServiceOperation,
            operationIdentifier = UserProvidedServiceConfig::name,
            operationDescription = { service -> "Create user provided service ${service.name}"},
            operationConfigQueue = queue,
            retries = retryCount
        )

        val flux = createQueueBackedFlux(queue)
        flux.subscribe(subscriber)

        return subscriber.results.get()
    }

    private fun createUserProvidedService(existingServices: List<String>, serviceConfig: UserProvidedServiceConfig): Mono<OperationResult> {
        val serviceCommand = if (existingServices.contains(serviceConfig.name)) {
            cloudFoundryClient.updateUserProvidedService(serviceConfig)
        } else {
            cloudFoundryClient.createUserProvidedService(serviceConfig)
        }

        val description = "Creating user provided service ${serviceConfig.name}"
        val operationResult = OperationResult(
            name = description,
            didSucceed = true
        )

        return serviceCommand
            .transform(logAsyncOperation(logger, description))
            .then(Mono.just(operationResult))
    }
}

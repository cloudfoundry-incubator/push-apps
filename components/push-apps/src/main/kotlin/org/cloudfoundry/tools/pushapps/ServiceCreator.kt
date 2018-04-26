package org.cloudfoundry.tools.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.tools.pushapps.config.ServiceConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ServiceCreator(
    private val serviceConfigs: List<ServiceConfig>,
    private val cloudFoundryClient: CloudFoundryClient,
    private val maxInFlight: Int
) {
    private val logger = LogManager.getLogger(ServiceCreator::class.java)

    fun createServices(): Flux<OperationResult> {
        val servicesToCreate = filterExistingServices(serviceConfigs)
        val serviceNames = servicesToCreate.map(ServiceConfig::name)
        logger.info("Creating services: ${serviceNames.joinToString(", ")}.")

        return scheduleOperations(
            configs = servicesToCreate,
            maxInFlight = maxInFlight,
            operation = this::createService,
            operationIdentifier = ServiceConfig::name,
            operationDescription = this::createServiceDescription
        )
    }

    private fun filterExistingServices(configs: List<ServiceConfig>): List<ServiceConfig> {
        val existingServiceNames = cloudFoundryClient
            .listServices()
            .toIterable()
            .toList()

        return configs.filter { serviceConfig ->
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

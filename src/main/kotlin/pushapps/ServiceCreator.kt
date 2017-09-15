package pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

class ServiceCreator(
    private val cloudFoundryClient: CloudFoundryClient,
    private val serviceConfigs: List<ServiceConfig>
) {
    fun createServices(): List<OperationResult> {
        val servicesToBeCreated = filterExistingServices()

        val createServicesFlux: Flux<OperationResult> = Flux.create { sink ->
            val createServiceFutures = servicesToBeCreated.map { serviceConfig ->
                generateServiceFuture(serviceConfig)
                    .thenApply { sink.next(it) }
            }

            CompletableFuture.allOf(*createServiceFutures
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        return createServicesFlux.toIterable().toList()
    }

    private fun filterExistingServices(): List<ServiceConfig> {
        val existingServiceNames = cloudFoundryClient.listServices()

        return serviceConfigs.filter { serviceConfig ->
            !existingServiceNames.contains(serviceConfig.name)
        }
    }

    private fun generateServiceFuture(serviceConfig: ServiceConfig): CompletableFuture<OperationResult> {
        return cloudFoundryClient
            .createService(serviceConfig)
            .toFuture()
            .thenApply {
                OperationResult(
                    name = serviceConfig.name,
                    didSucceed = true
                )
            }
            .exceptionally { error ->
                OperationResult(
                    name = serviceConfig.name,
                    didSucceed = false,
                    error = error
                )
            }
    }
}
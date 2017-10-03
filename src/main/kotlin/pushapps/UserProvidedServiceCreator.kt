package pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

class UserProvidedServiceCreator(
    private val cloudFoundryClient: CloudFoundryClient,
    private val serviceConfigs: List<UserProvidedServiceConfig>
) {
    fun createOrUpdateServices(): List<OperationResult> {
        val createServicesFlux: Flux<OperationResult> = Flux.create { sink ->
            val createOrUpdateServiceFutures = serviceConfigs.map { serviceConfig ->
                generateServiceFuture(serviceConfig)
                    .thenApply { sink.next(it) }
            }

            CompletableFuture.allOf(*createOrUpdateServiceFutures
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        return createServicesFlux.toIterable().toList()
    }

    private fun generateServiceFuture(serviceConfig: UserProvidedServiceConfig): CompletableFuture<OperationResult> {
        val existingServiceNames = cloudFoundryClient.listServices()

        val serviceCommand = if (existingServiceNames.contains(serviceConfig.name)) {
            cloudFoundryClient.updateUserProvidedService(serviceConfig)
        } else {
            cloudFoundryClient.createUserProvidedService(serviceConfig)
        }

        val serviceFuture = serviceCommand
            .toFuture()

        return getOperationResult(serviceFuture, serviceConfig.name, false)
    }
}
package pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

class UserProvidedServiceCreator(
    private val cloudFoundryClient: CloudFoundryClient,
    private val userProvidedServiceConfigs: List<UserProvidedServiceConfig>
) {
    fun createServices(): List<OperationResult> {
        val createServicesFlux: Flux<OperationResult> = Flux.create { sink ->
            val createServices = userProvidedServiceConfigs.map { serviceConfig ->
                createServiceFuture(serviceConfig)
                    .thenApply {
                        sink.next(it)
                    }
            }

            CompletableFuture.allOf(*createServices
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        return createServicesFlux.toIterable().toList()
    }

    private fun createServiceFuture(serviceConfig: UserProvidedServiceConfig): CompletableFuture<OperationResult> {
        return cloudFoundryClient
            .createUserProvidedService(serviceConfig)
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
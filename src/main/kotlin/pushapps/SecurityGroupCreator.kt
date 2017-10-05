package pushapps

import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

class SecurityGroupCreator(
    val securityGroups: List<SecurityGroup>,
    val cloudFoundryClient: CloudFoundryClient,
    val space: String
) {
    fun createSecurityGroups(): List<OperationResult> {
        val spaceId = cloudFoundryClient.getSpaceId(space)
        val securityGroupFutures = securityGroups.map { group ->
            val securityGroupFuture = cloudFoundryClient.createSecurityGroup(group, spaceId).toFuture()
            getOperationResult(securityGroupFuture, group.name, false)
        }

        val createSecurityGroupsFlux: Flux<OperationResult> = Flux.create { sink ->
            securityGroupFutures.map { securityGroupFuture ->
                securityGroupFuture
                    .thenApply { sink.next(it) }
            }

            CompletableFuture.allOf(*securityGroupFutures
                .toTypedArray())
                .thenApply { sink.complete() }
        }

        val results = createSecurityGroupsFlux.toIterable().toList()
        return results
    }
}
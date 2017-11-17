package io.pivotal.pushapps

import org.cloudfoundry.client.v2.ClientV2Exception
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

class SecurityGroupCreator(
    val securityGroups: List<SecurityGroup>,
    val cloudFoundryClient: CloudFoundryClient,
    val space: String
) {
    fun createSecurityGroups(): List<OperationResult> {
        val spaceId = cloudFoundryClient.getSpaceId(space)

        if (spaceId === null) return listOf(OperationResult(
            "Get space Id",
            false,
            PushAppsError("Could not find space id for space $space")
        ))

        val securityGroupFutures = securityGroups.map { group ->
            val securityGroupFuture = cloudFoundryClient
                .createSecurityGroup(group, spaceId)
                .toFuture()
                .handle { result, e ->
                    if (e === null || (e as ClientV2Exception).description.contains("security group name is taken")) {
                        result
                    } else {
                        throw e
                    }
                }

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

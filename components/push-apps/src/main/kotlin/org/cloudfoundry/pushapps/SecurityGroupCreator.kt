package org.cloudfoundry.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.client.v2.ClientV2Exception
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentLinkedQueue

class SecurityGroupCreator(
    private val securityGroups: List<SecurityGroup>,
    private val cloudFoundryClient: CloudFoundryClient,
    private val space: String, //TODO: not used
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger = LogManager.getLogger(SecurityGroupCreator::class.java)

    fun createSecurityGroups(spaceId: String): Flux<OperationResult> {
        val securityGroupNames = securityGroups.map(SecurityGroup::name)
        logger.info("Creating security groups: ${securityGroupNames.joinToString(", ")}.")

        val queue = ConcurrentLinkedQueue<SecurityGroup>()
        queue.addAll(securityGroups)

        return Flux.create<OperationResult> { sink ->
            val subscriber = OperationScheduler(
                maxInFlight = maxInFlight,
                sink = sink,
                operation = { group: SecurityGroup -> createSecurityGroup(spaceId, group) },
                operationIdentifier = SecurityGroup::name,
                operationDescription = { group -> "Create security group ${group.name}" },
                operationConfigQueue = queue
            )

            val flux = createQueueBackedFlux(queue)
            flux.subscribe(subscriber)
        }
    }

    private fun createSecurityGroup(spaceId: String, group: SecurityGroup): Mono<OperationResult> {
        val description = "Create security group ${group.name}"
        val operationResult = OperationResult(
            description = description,
            didSucceed = true,
            operationConfig = group
        )

        return cloudFoundryClient
            .createSecurityGroup(group, spaceId)
            .onErrorResume { e: Throwable ->
                if ((e as ClientV2Exception).description.contains("security group name is taken")) {
                    return@onErrorResume Mono.empty()
                }

                throw e
            }
            .transform(logAsyncOperation(logger, description))
            .then(Mono.just(operationResult))
    }
}

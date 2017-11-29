package io.pivotal.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.client.v2.ClientV2Exception
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentLinkedQueue

class SecurityGroupCreator(
    private val securityGroups: List<SecurityGroup>,
    private val cloudFoundryClient: CloudFoundryClient,
    private val space: String,
    private val maxInFlight: Int,
    private val retryCount: Int
) {
    private val logger = LogManager.getLogger(SecurityGroupCreator::class.java)

    fun createSecurityGroups(): List<OperationResult> {
        val spaceId = cloudFoundryClient.getSpaceId(space)

        if (spaceId === null) return listOf(OperationResult(
            "Get space Id",
            false,
            PushAppsError("Could not find space id for space $space")
        ))

        val securityGroupNames = securityGroups.map(SecurityGroup::name)
        logger.info("Creating security groups: ${securityGroupNames.joinToString(", ")}.")

        val queue = ConcurrentLinkedQueue<SecurityGroup>()
        queue.addAll(securityGroups)

        val subscriber = OperationScheduler<SecurityGroup>(
            maxInFlight = maxInFlight,
            operation = { group: SecurityGroup -> createSecurityGroup(spaceId, group) },
            operationIdentifier = SecurityGroup::name,
            operationConfigQueue = queue,
            retries = retryCount
        )

        val flux = createQueueBackedFlux(queue)
        flux.subscribe(subscriber)

        return subscriber.results.get()
    }

    private fun createSecurityGroup(spaceId: String, group: SecurityGroup): Mono<OperationResult> {
        val description = "Create security group ${group.name}"
        val operationResult = OperationResult(
            name = description,
            didSucceed = true
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

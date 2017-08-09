package pushapps

import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.applications.PushApplicationRequest
import org.cloudfoundry.operations.spaces.CreateSpaceRequest
import reactor.core.publisher.Mono

class CloudFoundryClient(
    apiHost: String,
    password: String,
    username: String,
    organization: String
) {

    private val cloudFoundryOperations = CloudFoundryOperationsBuilder
        .builder()
        .apiHost(apiHost)
        .username(username)
        .password(password)
        .organization(organization)
        .build()

    fun listApplications(): MutableIterable<ApplicationSummary> {
        return cloudFoundryOperations.applications().list().toIterable()
    }

    fun listSpaces(): List<String> {
        return cloudFoundryOperations.spaces().list().toIterable().map {
            it.name
        }
    }

    fun createSpaceIfDoesNotExist(name: String) {
        if (!spaceDoesExist(name)) {
            createSpace(name)
        }
    }

    fun pushApplication(pushRequest: PushApplicationRequest): Mono<Void> {
        return cloudFoundryOperations
            .applications()
            .push(pushRequest)
    }

    private fun spaceDoesExist(name: String) = listSpaces().indexOf(name) != -1

    private fun createSpace(name: String) {
        val createSpaceRequest: CreateSpaceRequest = CreateSpaceRequest
            .builder()
            .name(name)
            .build()

        cloudFoundryOperations.spaces().create(createSpaceRequest).block()
    }
}
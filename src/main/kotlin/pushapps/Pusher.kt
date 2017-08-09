package pushapps

import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.applications.PushApplicationRequest
import java.io.File

class Pusher(
    val apiHost: String,
    val password: String,
    val username: String,
    val organization: String
) {
    private val cloudFoundryClient: CloudFoundryClient = CloudFoundryClient(
        apiHost,
        password,
        username,
        organization
    )

    fun list() {
        val then: MutableIterable<ApplicationSummary> = cloudFoundryClient.listApplications()

        println("Found apps: ")
        then.map {
            println(it.name)
        }
    }

    fun push(name: String, path: String, buildpack: String) {
        val file = File(path)


        val pushAppRequest = PushApplicationRequest
            .builder()
            .name(name)
            .path(file.toPath())
            .noStart(true)
            .buildpack(buildpack)
            .build()

        val push = cloudFoundryClient.pushApplication(pushAppRequest)
            .doOnError { println("Failed to push $name, ${it.message}") }
            .doOnSuccess { println("************* SUCCESS **************") }

        try {
            push.block()
        } catch (e: Exception) {
            println("!!!!!!!!!!!!!!!!!!")
            println("Caught ${e.message}")
        }
    }
}
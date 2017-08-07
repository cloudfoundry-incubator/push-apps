package pushapps

import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.applications.PushApplicationRequest
import org.cloudfoundry.reactor.ConnectionContext
import org.cloudfoundry.reactor.DefaultConnectionContext
import org.cloudfoundry.reactor.TokenProvider
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider
import org.cloudfoundry.reactor.uaa.ReactorUaaClient
import java.io.File

class Pusher(
    val apiHost: String,
    val password: String,
    val username: String,
    val organization: String,
    val space: String
) {

    fun list() {
        val connectionContext = connectionContext()
        val tokenProvider = tokenProvider()

        val client = buildClient(connectionContext, tokenProvider)

        val then: MutableIterable<ApplicationSummary> = client.applications().list().toIterable()

        println("Found apps: ")
        then.map {
            println(it.name)
        }
    }

    fun push(name: String, path: String, buildpack: String) {
        val connectionContext = connectionContext()
        val tokenProvider = tokenProvider()

        val client = buildClient(connectionContext, tokenProvider)
        val file = File(path)


        val pushAppRequest = PushApplicationRequest
            .builder()
            .name(name)
            .path(file.toPath())
            .noStart(true)
            .buildpack(buildpack)
            .build()

        val push = client
            .applications()
            .push(pushAppRequest)
            .doOnError { println("Failed to push $name, ${it.message}") }
            .doOnSuccess { println("************* SUCCESS **************") }

        try {
            push.block()
        } catch (e: Exception) {
            println("!!!!!!!!!!!!!!!!!!")
            println("Caught ${e.message}")
        }
    }

    private fun buildClient(connectionContext: ConnectionContext, tokenProvider: TokenProvider): DefaultCloudFoundryOperations {
        val cfClient = ReactorCloudFoundryClient.builder()
            .connectionContext(connectionContext)
            .tokenProvider(tokenProvider)
            .build()

        val dopplerClient = ReactorDopplerClient.builder()
            .connectionContext(connectionContext)
            .tokenProvider(tokenProvider)
            .build()

        val uaaClient = ReactorUaaClient.builder()
            .connectionContext(connectionContext)
            .tokenProvider(tokenProvider)
            .build()

        return DefaultCloudFoundryOperations.builder()
            .cloudFoundryClient(cfClient)
            .dopplerClient(dopplerClient)
            .uaaClient(uaaClient)
            .organization(organization)
            .space(space)
            .build()
    }

    private fun connectionContext(): ConnectionContext {
        return DefaultConnectionContext.builder()
            .apiHost(apiHost)
            .skipSslValidation(true)
            .build()
    }

    private fun tokenProvider(): TokenProvider {
        return PasswordGrantTokenProvider.builder()
            .password(password)
            .username(username)
            .build()
    }
}
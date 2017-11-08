package io.pivotal.pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.reactor.ConnectionContext
import org.cloudfoundry.reactor.DefaultConnectionContext
import org.cloudfoundry.reactor.TokenProvider
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider
import org.cloudfoundry.reactor.uaa.ReactorUaaClient
import java.time.Duration

class CloudFoundryOperationsBuilder {
    var apiHost: String? = null
    var username: String? = null
    var password: String? = null
    var organization: String? = null
    var space: String? = null
    var skipSslValidation: Boolean = false
    var existingCloudFoundryOperations: CloudFoundryOperations? = null
    var dialTimeoutInMillis: Long? = null

    companion object {
        @JvmStatic
        fun cloudFoundryOperationsBuilder(): CloudFoundryOperationsBuilder {
            return CloudFoundryOperationsBuilder()
        }
    }

    fun build(): CloudFoundryOperations {
        if (existingCloudFoundryOperations !== null) {
            return buildCfOperationsFromExisting(existingCloudFoundryOperations!!)
        } else {
            val connectionContext = connectionContext()
            val tokenProvider = tokenProvider()

            return buildCfOperations(connectionContext, tokenProvider)
        }
    }

    fun fromExistingOperations(operations: CloudFoundryOperations): CloudFoundryOperationsBuilder {
        this.existingCloudFoundryOperations = operations
        return this
    }

    private fun buildCfOperationsFromExisting(cloudFoundryOperations: CloudFoundryOperations): DefaultCloudFoundryOperations {
        val cfOperationsBuilder = DefaultCloudFoundryOperations
            .builder()
            .from(cloudFoundryOperations as DefaultCloudFoundryOperations)

        if (organization !== null) cfOperationsBuilder.organization(organization)
        if (space !== null) cfOperationsBuilder.space(space)

        return cfOperationsBuilder.build()
    }

    private fun buildCfOperations(connectionContext: ConnectionContext, tokenProvider: TokenProvider): DefaultCloudFoundryOperations {
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

        val cfOperationsBuilder = DefaultCloudFoundryOperations.builder()
            .cloudFoundryClient(cfClient)
            .dopplerClient(dopplerClient)
            .uaaClient(uaaClient)

        if (organization !== null) cfOperationsBuilder.organization(organization)
        if (space !== null) cfOperationsBuilder.space(space)

        return cfOperationsBuilder.build()
    }

    private fun connectionContext(): ConnectionContext {
        val connectionContextBuilder = DefaultConnectionContext.builder()
        if (apiHost !== null) connectionContextBuilder.apiHost(apiHost)
        if (dialTimeoutInMillis !== null) connectionContextBuilder.connectTimeout(Duration.ofMillis(dialTimeoutInMillis!!))

        return connectionContextBuilder
            .skipSslValidation(skipSslValidation)
            .build()
    }

    private fun tokenProvider(): TokenProvider {
        val tokenProviderBuilder = PasswordGrantTokenProvider.builder()

        if (password !== null) tokenProviderBuilder.password(password)
        if (username !== null) tokenProviderBuilder.username(username)

        return tokenProviderBuilder.build()
    }
}

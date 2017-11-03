package io.pivotal.pushapps

import io.pivotal.pushapps.CloudFoundryOperationsBuilder.Companion.cloudFoundryOperationsBuilder
import org.cloudfoundry.operations.CloudFoundryOperations

class CloudFoundryClientBuilder(
    var cloudFoundryOperations: CloudFoundryOperations? = null,
    var cloudFoundryOperationsBuilder: CloudFoundryOperationsBuilder = cloudFoundryOperationsBuilder()
) {
    companion object {
        @JvmStatic
        fun cloudFoundryClientBuilder(): CloudFoundryClientBuilder {
            return CloudFoundryClientBuilder()
        }
    }

    fun build(): CloudFoundryClient {
        val cfOperations = cloudFoundryOperations

        if (cfOperations === null) {
            throw UnsupportedOperationException("Must provide a CloudFoundryOperations instance before building")
        }

        return CloudFoundryClient(
            cfOperations,
            cloudFoundryOperationsBuilder
        )
    }
}

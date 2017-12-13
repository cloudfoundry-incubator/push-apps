package org.cloudfoundry.pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.pushapps.CloudFoundryOperationsBuilder.Companion.cloudFoundryOperationsBuilder

class CloudFoundryClientBuilder(
    var cloudFoundryOperations: CloudFoundryOperations? = null,
    var cloudFoundryOperationsBuilder: CloudFoundryOperationsBuilder = cloudFoundryOperationsBuilder(),
    var operationTimeoutInMinutes: Long = 5L
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
            cloudFoundryOperationsBuilder,
            operationTimeoutInMinutes
        )
    }
}

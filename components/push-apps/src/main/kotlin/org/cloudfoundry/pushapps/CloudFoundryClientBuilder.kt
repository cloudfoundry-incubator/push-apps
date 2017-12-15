package org.cloudfoundry.pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.pushapps.CloudFoundryOperationsBuilder.Companion.cloudFoundryOperationsBuilder

class CloudFoundryClientBuilder(
    private var cloudFoundryOperationsBuilder: CloudFoundryOperationsBuilder = cloudFoundryOperationsBuilder(),
    var cloudFoundryOperations: CloudFoundryOperations? = null,
    var operationTimeoutInMinutes: Long = DEFAULT_OPERATION_TIMEOUT,
    var retryCount: Int = DEFAULT_OPERATION_RETRY_COUNT
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
            operationTimeoutInMinutes,
            retryCount
        )
    }
}

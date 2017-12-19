package org.cloudfoundry.tools.pushapps

import org.cloudfoundry.operations.CloudFoundryOperations

fun cloudFoundryClientBuilder(): CloudFoundryClientBuilder {
    return CloudFoundryClientBuilder()
}

class CloudFoundryClientBuilder(
    private var cloudFoundryOperationsBuilder: CloudFoundryOperationsBuilder = cloudFoundryOperationsBuilder(),
    var cloudFoundryOperations: CloudFoundryOperations? = null,
    var operationTimeoutInMinutes: Long = DEFAULT_OPERATION_TIMEOUT,
    var retryCount: Int = DEFAULT_OPERATION_RETRY_COUNT
) {
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

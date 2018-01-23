package org.cloudfoundry.tools.pushapps.config

val DEFAULT_OPERATION_RETRY_COUNT = 3
val DEFAULT_OPERATION_TIMEOUT = 5L

data class PushAppsConfig(
        val operationRetryCount: Int = DEFAULT_OPERATION_RETRY_COUNT,
        val maxInFlight: Int = 2,
        val failedDeploymentLogLinesToShow: Int = 50,
        val migrationTimeoutInMinutes: Long = 15L,
        val cfOperationTimeoutInMinutes: Long = DEFAULT_OPERATION_TIMEOUT
)
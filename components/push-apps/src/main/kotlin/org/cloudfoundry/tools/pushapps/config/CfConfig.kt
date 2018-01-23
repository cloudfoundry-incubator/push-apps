package org.cloudfoundry.tools.pushapps.config

data class CfConfig(
        val apiHost: String,
        val username: String,
        val password: String,
        val organization: String,
        val space: String,
        val skipSslValidation: Boolean = false,
        val dialTimeoutInMillis: Long? = null
)

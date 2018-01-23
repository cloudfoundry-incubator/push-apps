package org.cloudfoundry.tools.pushapps.config

data class ServiceConfig(
        override val name: String,
        val plan: String,
        val broker: String,
        override val optional: Boolean = false
) : OperationConfig

data class UserProvidedServiceConfig(
        override val name: String,
        val credentials: Map<String, Any>,
        override val optional: Boolean = false
) : OperationConfig
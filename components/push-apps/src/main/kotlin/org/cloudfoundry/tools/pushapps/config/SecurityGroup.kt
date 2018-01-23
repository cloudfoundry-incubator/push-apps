package org.cloudfoundry.tools.pushapps.config

data class SecurityGroup(
        override val name: String,
        val destination: String,
        val protocol: String,
        override val optional: Boolean = false
) : OperationConfig
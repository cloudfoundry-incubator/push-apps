package org.cloudfoundry.tools.pushapps.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule())

class ConfigReader {
    companion object {
        fun parseConfig(configPath: String) = mapper.readValue<Config>(File(configPath))
    }
}

data class Config(
        val pushApps: PushAppsConfig = PushAppsConfig(),
        val cf: CfConfig,
        val apps: List<AppConfig>,
        val services: List<ServiceConfig> = emptyList(),
        val userProvidedServices: List<UserProvidedServiceConfig> = emptyList(),
        val migrations: List<Migration> = emptyList(),
        val securityGroups: List<SecurityGroup> = emptyList()
)

interface OperationConfig {
    val name: String
    val optional: Boolean
}

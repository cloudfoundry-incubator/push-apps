package org.cloudfoundry.tools.pushapps.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.IOException
import java.util.*

val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule())

class ConfigReader {
    companion object {
        private val logger = LogManager.getLogger(ConfigReader::class.java)

        fun parseConfig(configPath: String): Optional<Config> {
            return try {
                Optional.ofNullable(mapper.readValue(File(configPath)))
            } catch (e: IOException) {
                logger.error("Error parsing config: ${e.message}")
                if (logger.isDebugEnabled) {
                    e.printStackTrace()
                }

                Optional.empty()
            }
        }
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

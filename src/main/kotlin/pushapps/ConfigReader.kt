package pushapps

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
    val pushApps: PushApps,
    val cf: CfConfig,
    val apps: List<AppConfig>,
    val services: List<ServiceConfig>? = emptyList(),
    val userProvidedServices: List<UserProvidedServiceConfig>? = emptyList(),
    val migrations: List<Migration>? = emptyList()
)

data class PushApps(
    val appDeployRetryCount: Int = 1
)

data class CfConfig(
    val apiHost: String,
    val username: String,
    val password: String,
    val organization: String,
    val space: String,
    val skipSslValidation: Boolean = false,
    val dialTimeoutInMillis: Long? = null
)

data class AppConfig(
    val name: String,
    val path: String,
    val buildpack: String? = null,
    val command: String? = null,
    val environment: Map<String, String>? = null,
    val instances: Int? = null,
    val diskQuota: Int? = null,
    val memory: Int? = null,
    val noHostname: Boolean? = null,
    val noRoute: Boolean? = null,
    val route: Route? = null,
    val timeout: Int? = null,
    val blueGreenDeploy: Boolean? = null,
    val domain: String? = null,
    val healthCheckType: String? = null,
    val serviceNames: List<String>? = emptyList()
)

data class Route(
    val hostname: String,
    val path: String? = null
)

data class ServiceConfig (
    val name: String,
    val plan: String,
    val broker: String,
    val optional: Boolean = false
)

data class UserProvidedServiceConfig(
    val name: String,
    val credentials: Map<String, Any>
)

data class Migration(
    val user: String,
    val password: String,
    val driver: String,
    val host: String,
    val port: String,
    val schema: String,
    val migrationDir: String
)

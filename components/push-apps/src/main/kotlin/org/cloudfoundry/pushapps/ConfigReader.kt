package org.cloudfoundry.pushapps

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
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

interface OperationConfig {
    val name: String
    val optional: Boolean
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

val DEFAULT_OPERATION_RETRY_COUNT = 3
val DEFAULT_OPERATION_TIMEOUT = 5L

data class PushAppsConfig(
    val operationRetryCount: Int = DEFAULT_OPERATION_RETRY_COUNT,
    val maxInFlight: Int = 2,
    val failedDeploymentLogLinesToShow: Int = 50,
    val migrationTimeoutInMinutes: Long = 15L,
    val cfOperationTimeoutInMinutes: Long = DEFAULT_OPERATION_TIMEOUT
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
    override val name: String,
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
    val serviceNames: List<String> = emptyList(),
    override val optional: Boolean = false
) : OperationConfig

data class Route(
    val hostname: String,
    val path: String? = null
)

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

sealed class DatabaseDriver(val name: String) {
    class MySql : DatabaseDriver("mysql")
    class Postgres : DatabaseDriver("postgres")
}

@JsonDeserialize(using = MigrationDeserializer::class)
@JsonSerialize(using = MigrationSerializer::class)
data class Migration(
    val user: String,
    val password: String,
    val driver: DatabaseDriver,
    val host: String,
    val port: String,
    val schema: String,
    val migrationDir: String,
    val repair: Boolean,
    override val name: String = "Migrate schema $schema",
    override val optional: Boolean = false
): OperationConfig

class MigrationDeserializer : StdDeserializer<Migration>(Migration::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Migration {
        val node = p.codec.readTree<JsonNode>(p)

        val user = node.get("user").asText()
        val password = node.get("password").asText()
        val host = node.get("host").asText()
        val port = node.get("port").asText()
        val schema = node.get("schema").asText()
        val migrationDir = node.get("migrationDir").asText()

        val repair = if (node.has("repair")) {
            node.get("repair").asBoolean()
        } else {
            false
        }

        val driverString = node.get("driver").asText()
        val driver = when (driverString) {
            "mysql" -> DatabaseDriver.MySql()
            else -> DatabaseDriver.Postgres()
        }

        return Migration(
            user,
            password,
            driver,
            host,
            port,
            schema,
            migrationDir,
            repair
        )
    }
}

//FIXME: this is only used for test, consider another approach
class MigrationSerializer : StdSerializer<Migration>(Migration::class.java) {
    override fun serialize(value: Migration, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject();
        gen.writeStringField("user", value.user);
        gen.writeStringField("password", value.password);
        gen.writeStringField("host", value.host);
        gen.writeStringField("port", value.port);
        gen.writeStringField("schema", value.schema);
        gen.writeStringField("migrationDir", value.migrationDir);
        gen.writeBooleanField("repair", value.repair);

        val driver = when (value.driver) {
            is DatabaseDriver.MySql -> "mysql"
            is DatabaseDriver.Postgres -> "postgres"
        }

        gen.writeStringField("driver", driver);

        gen.writeEndObject();
    }
}

data class SecurityGroup(
    override val name: String,
    val destination: String,
    val protocol: String,
    override val optional: Boolean = false
) : OperationConfig

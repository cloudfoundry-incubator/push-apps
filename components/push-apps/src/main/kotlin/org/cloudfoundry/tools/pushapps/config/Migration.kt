package org.cloudfoundry.tools.pushapps.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer


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
    val placeholders: Map<String, String>,
    override val name: String = "Migrate schema $schema",
    override val optional: Boolean = false
) : OperationConfig

class MigrationDeserializer : StdDeserializer<Migration>(Migration::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Migration {
        val node = p.codec.readTree<JsonNode>(p)

        val user = node.get("user").asText()
        val password = node.get("password").asText()
        val host = node.get("host").asText()
        val port = node.get("port").asText()
        val schema = node.get("schema").asText()
        val migrationDir = node.get("migrationDir").asText()

        val placeholders = mutableMapOf<String, String>()
        if (node.has("placeholders")) {
            for (placeholder in node.get("placeholders").fields()) {
                placeholders[placeholder.key] = placeholder.value.asText()
            }
        }

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
            repair,
            placeholders
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

sealed class DatabaseDriver(val name: String) {
    class MySql : DatabaseDriver("mysql")
    class Postgres : DatabaseDriver("postgres")
}
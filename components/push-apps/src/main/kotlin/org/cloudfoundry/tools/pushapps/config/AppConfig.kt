package org.cloudfoundry.tools.pushapps.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

@JsonDeserialize(using = ApplicationDeserializer::class)
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


class ApplicationDeserializer : StdDeserializer<AppConfig>(AppConfig::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AppConfig {
        val node = p.codec.readTree<JsonNode>(p)

        val mapper = ObjectMapper()
        val envNode = node.get("environment")
        val environment = mapper.convertValue(envNode, Map::class.java)

        val routeNode = node.get("route")
        val route = mapper.convertValue(routeNode, Route::class.java)

        val serviceNamesNode = node.get("serviceNames")
        val serviceNames = mapper.convertValue(serviceNamesNode, List::class.java) as? List<String> ?: emptyList()

        val memoryText = node.get("memory")?.asText()
        val memory = convertToMegabytes(memoryText)

        val diskQuotaText = node.get("diskQuota")?.asText()
        val diskQuota = convertToMegabytes(diskQuotaText)

        val name = node.get("name").asText()
        val path = node.get("path").asText()
        val buildpack = node.get("buildpack")?.asText()
        val command = node.get("command")?.asText()
        val instances = node.get("instances")?.asInt()
        val noHostname = node.get("noHostname")?.asBoolean()
        val noRoute = node.get("noRoute")?.asBoolean()
        val timeout = node.get("timeout")?.asInt()
        val blueGreenDeploy = node.get("blueGreenDeploy")?.asBoolean()
        val domain = node.get("domain")?.asText()
        val healthCheckType = node.get("healthCheckType")?.asText()
        val optional = node.get("optional")?.asBoolean() ?: false
        return AppConfig(
                name,
                path,
                buildpack,
                command,
                environment as? Map<String, String>,
                instances,
                diskQuota,
                memory,
                noHostname,
                noRoute,
                route,
                timeout,
                blueGreenDeploy,
                domain,
                healthCheckType,
                serviceNames,
                optional
        )
    }

    private fun convertToMegabytes(memoryText: String?): Int? {
        var memoryTextWithoutSuffix = memoryText
        if (memoryTextWithoutSuffix == null) {
            return null
        }

        var multiplier = 1
        if (memoryTextWithoutSuffix.endsWith("M", true)) {
            memoryTextWithoutSuffix = memoryTextWithoutSuffix.dropLast(1)
        }

        else if (memoryTextWithoutSuffix.endsWith("G", true)) {
            memoryTextWithoutSuffix = memoryTextWithoutSuffix.dropLast(1)
            multiplier = 1024
        }
        return memoryTextWithoutSuffix.toInt() * multiplier
    }
}
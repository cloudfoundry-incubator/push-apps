package org.cloudfoundry.tools.pushapps.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import java.io.IOException

@JsonDeserialize(using = ApplicationDeserializer::class)
data class AppConfig(
        override val name: String,
        val path: String,
        val buildpack: String? = null,
        val command: String? = null,
        val environment: Map<String, String?>? = null,
        val instances: Int? = null,
        val diskQuota: Int? = null,
        val memory: Int? = null,
        val noHostname: Boolean? = null,
        val noRoute: Boolean = false,
        val route: Route? = null,
        val stackPriority: List<String> = emptyList(),
        val timeout: Int? = null,
        val blueGreenDeploy: Boolean = false,
        val domain: String? = null,
        val healthCheckType: String? = null,
        val serviceNames: List<String> = emptyList(),
        override val optional: Boolean = false
) : OperationConfig


class ApplicationDeserializer : StdDeserializer<AppConfig>(AppConfig::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AppConfig {
        val node = p.codec.readTree<JsonNode>(p)
        val mapper = ObjectMapper().registerModule(KotlinModule())

        val blueGreenDeploy: Boolean = node.get("blueGreenDeploy")?.asBoolean() ?: false
        val noRoute: Boolean = node.get("noRoute")?.asBoolean() ?: false
        val route = if (node.has("route")) mapper.convertValue(node.get("route"), Route::class.java) else null

        if (!blueGreenRequirementsMet(blueGreenDeploy, noRoute, route)) {
            throw IOException("When doing a blue green deployment, either a route must be provided, or noRoute must be set to true.")
        }

        val environment: Map<String, String>? = if (node.has("environment")) {
            val typeReference = jacksonTypeRef<Map<String, String>>()
            mapper.convertValue<Map<String, String>>(node.get("environment"), typeReference)
        } else null

        val serviceNames = if (node.has("serviceNames")) {
            val typeReference = jacksonTypeRef<List<String>>()
            mapper.convertValue<List<String>>(node.get("serviceNames"), typeReference)
        } else emptyList()

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
        val timeout = node.get("timeout")?.asInt()
        val domain = node.get("domain")?.asText()
        val healthCheckType = node.get("healthCheckType")?.asText()
        val optional = node.get("optional")?.asBoolean() ?: false
        val stackPriority = if (node.has("stackPriority")) {
            val typeReference = jacksonTypeRef<List<String>>()
            mapper.convertValue<List<String>>(node.get("stackPriority"), typeReference)
        } else emptyList()

        return AppConfig(
            name,
            path,
            buildpack,
            command,
            environment,
            instances,
            diskQuota,
            memory,
            noHostname,
            noRoute,
            route,
            stackPriority,
            timeout,
            blueGreenDeploy,
            domain,
            healthCheckType,
            serviceNames,
            optional
        )
    }

    private fun blueGreenRequirementsMet(blueGreenDeploy: Boolean, noRoute: Boolean, route: Route?) =
        if (blueGreenDeploy) (noRoute || route !== null) else true

    private fun convertToMegabytes(memoryText: String?): Int? {
        var memoryTextWithoutSuffix = memoryText
        if (memoryTextWithoutSuffix == null) {
            return null
        }

        var multiplier = 1
        if (memoryTextWithoutSuffix.endsWith("M", true)) {
            memoryTextWithoutSuffix = memoryTextWithoutSuffix.dropLast(1)
        } else if (memoryTextWithoutSuffix.endsWith("G", true)) {
            memoryTextWithoutSuffix = memoryTextWithoutSuffix.dropLast(1)
            multiplier = 1024
        }
        return memoryTextWithoutSuffix.toInt() * multiplier
    }
}
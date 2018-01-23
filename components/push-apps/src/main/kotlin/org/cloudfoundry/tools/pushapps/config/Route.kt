package org.cloudfoundry.tools.pushapps.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

@JsonDeserialize(using = RouteDeserializer::class)
data class Route(
        val hostname: String,
        val path: String? = null
)

class RouteDeserializer: StdDeserializer<Route>(Route::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Route {
        val node = p.codec.readTree<JsonNode>(p)

        val hostname = node.get("hostname").asText()
        val path = node.get("path")?.asText()

        return Route(hostname = hostname, path = path)
    }
}
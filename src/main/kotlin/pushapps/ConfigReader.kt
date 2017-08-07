package pushapps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

class ConfigReader {
    companion object {
        fun parseConfig(configPath: String) = mapper.readValue(File(configPath), Config::class.java)
    }
}

data class Config(val cf: Cf, val apps: Array<App>)

data class Cf(
    val apiHost: String,
    val username: String,
    val password: String,
    val organization: String,
    val space: String
)

data class App(
    val name: String,
    val path: String,
    val buildpack: String,
    val environment: Map<String, String>
)
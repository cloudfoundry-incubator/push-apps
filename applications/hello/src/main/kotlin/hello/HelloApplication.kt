package hello

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@EnableAutoConfiguration
class HelloApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(HelloApplication::class.java)
        }
    }

    @RequestMapping("/")
    @ResponseBody
    fun root(): String {
        val objectMapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(KotlinModule())

        val name = System.getenv("NAME")
        val verb = System.getenv("VERB")
        val animalType = System.getenv("ANIMAL_TYPE")
        val animalName = System.getenv("ANIMAL_NAME")
        val hour = System.getenv("HOUR")
        val minute = System.getenv("MINUTE")

        val vcapServices = System.getenv("VCAP_SERVICES")
        val vcapJson = objectMapper.readValue<VcapServices>(vcapServices)
        val complimentService = vcapJson.userProvided.find { it.name == "compliment-service" }
        val compliment = complimentService!!.credentials["compliment"]

        return "hello $name, you are $compliment!\nYou have these services: $vcapServices.\n" +
            "Did you remember to $verb your $animalType named $animalName at $hour:$minute?\n"
    }
}

data class VcapServices(
    @JsonProperty("user-provided") val userProvided: List<Service>
)

data class Service(
    @JsonProperty("name") val name: String,
    @JsonProperty("credentials") val credentials: Map<String, Any>
)

package goodbye

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@EnableAutoConfiguration
class GoodbyeApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(GoodbyeApplication::class.java)
        }
    }

    @RequestMapping("/")
    @ResponseBody
    fun root(): String {
        val name = System.getenv("NAME")
        return "goodbye $name\n"
    }
}

package pushapps

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

class Args {
    @Parameter(names = arrayOf("-name"), description = "Person to greet")
    var name: String = "World"
}

fun main(args: Array<String>) {
    var parsedArgs = Args()

    val argsParser = JCommander
            .newBuilder()
            .addObject(parsedArgs)
            .build()

    if (argsParser == null) {
        println("Couldn't parse command line args")
    }

    argsParser.parse(*args)

    println("Hello ${parsedArgs.name}")
}
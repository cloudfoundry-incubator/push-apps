package pushapps

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

class ArgumentParser {
    companion object {
        fun parseConfigPath(args: Array<String>): String {
            val parsedArgs = Args()

            try {
                JCommander
                    .newBuilder()
                    .addObject(parsedArgs)
                    .build()
                    .parse(*args)
            } catch (e: Exception) {
                throw Exception("Couldn't parseConfigPath command line args")
            }

            return parsedArgs.configPath!!
        }
    }
}

class Args {
    @Parameter(names = arrayOf("-c", "--config"), description = "Path to configuration YAML", required = true)
    var configPath: String? = null
}

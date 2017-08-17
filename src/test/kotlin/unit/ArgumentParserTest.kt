package unit

import io.damo.aspen.Test
import org.assertj.core.api.Assertions.*
import pushapps.ArgumentParser

class ArgumentParserTest : Test({
    describe("#parseConfigPath") {
        test("Parses the arguments from command line") {
            val configPath = ArgumentParser.parseConfigPath(arrayOf("-c", "example.yml"))
            assertThat(configPath).isEqualTo("example.yml")
        }

        test("Raises an exception if it cannot parse config path") {
            assertThatThrownBy { ArgumentParser.parseConfigPath(arrayOf("-q", "")) }
                .hasMessage("Couldn't parseConfigPath command line args")
        }
    }
})
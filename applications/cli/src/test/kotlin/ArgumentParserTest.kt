import org.cloudfoundry.pushappscli.ArgumentParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class ArgumentParserTest : Spek({
    describe("#parseConfigPath") {
        it("Parses the arguments from command line") {
            val configPath = ArgumentParser.parseConfigPath(arrayOf("-c", "example.yml"))
            assertThat(configPath).isEqualTo("example.yml")
        }

        it("Raises an exception if it cannot parse config path") {
            assertThatThrownBy { ArgumentParser.parseConfigPath(arrayOf("-q", "")) }
                .hasMessage("Couldn't parseConfigPath command line args")
        }
    }
})

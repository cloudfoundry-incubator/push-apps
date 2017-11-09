package io.pivotal.pushappscli

import io.pivotal.pushapps.CloudFoundryClientBuilder.Companion.cloudFoundryClientBuilder
import io.pivotal.pushapps.ConfigReader
import io.pivotal.pushapps.PushApps
import org.apache.logging.log4j.LogManager

class PushAppsCli {
    companion object {
        private val logger = LogManager.getLogger(PushAppsCli::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val configPath: String = ArgumentParser.parseConfigPath(args)
            val config = ConfigReader.parseConfig(configPath)

            val pushApps = PushApps(
                config,
                cloudFoundryClientBuilder()
            )

            val result = pushApps.pushApps()

            if (!result) System.exit(3)

            //TODO capture errors and print
            logger.info("SUCCESS")
        }
    }
}

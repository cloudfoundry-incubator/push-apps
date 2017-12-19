package org.cloudfoundry.tools.pushappscli

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.tools.pushapps.CloudFoundryClientBuilder.Companion.cloudFoundryClientBuilder
import org.cloudfoundry.tools.pushapps.ConfigReader
import org.cloudfoundry.tools.pushapps.PushApps

class PushAppsCli {
    companion object {
        private val logger = LogManager.getLogger(PushAppsCli::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val configPath: String = ArgumentParser.parseConfigPath(args)
            val config = ConfigReader.parseConfig(configPath)

            logger.info("Pushing applications to the platform")
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

package org.cloudfoundry.pushappscli

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.pushapps.CloudFoundryClientBuilder.Companion.cloudFoundryClientBuilder
import org.cloudfoundry.pushapps.ConfigReader
import org.cloudfoundry.pushapps.PushApps

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

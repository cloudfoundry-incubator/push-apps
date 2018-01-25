package org.cloudfoundry.tools.pushapps

import org.apache.logging.log4j.LogManager
import org.cloudfoundry.tools.pushapps.config.ConfigReader

class PushAppsCli {
    companion object {
        private val logger = LogManager.getLogger(PushAppsCli::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val configPath: String = ArgumentParser.parseConfigPath(args)
            val config = ConfigReader.parseConfig(configPath)

            if (config.isPresent) {
                logger.info("Pushing applications to the platform")
                val pushApps = PushApps(
                    config.get(),
                    cloudFoundryClientBuilder()
                )

                val result = pushApps.pushApps()

                if (!result) System.exit(3)
            } else {
                System.exit(3)
            }

            logger.info("SUCCESS")
        }
    }
}

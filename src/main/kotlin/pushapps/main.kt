package pushapps

fun main(args: Array<String>) {
    val configPath = ArgumentParser.parseConfigPath(args)
    val (cf, apps) = ConfigReader.parseConfig(configPath)

    val cloudFoundryClient = CloudFoundryClient(
        cf.apiHost,
        cf.username,
        cf.password,
        cf.organization
    )

    cloudFoundryClient.createSpaceIfDoesNotExist(cf.space)
}
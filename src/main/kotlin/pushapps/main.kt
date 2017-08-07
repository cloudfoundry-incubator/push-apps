package pushapps

fun main(args: Array<String>) {
    val configPath = ArgumentParser.parseConfigPath(args)
    val (cf, apps) = ConfigReader.parseConfig(configPath)

    val pusher = Pusher(apiHost = cf.apiHost,
        password = cf.password,
        username = cf.username,
        organization = cf.organization,
        space = cf.space
    )

    pusher.list()
    val app = apps[0]
    pusher.push(app.name, app.path, app.buildpack)
}
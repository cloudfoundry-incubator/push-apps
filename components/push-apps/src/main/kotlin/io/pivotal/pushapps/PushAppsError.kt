package io.pivotal.pushapps

class PushAppsError(message: String, cause: Throwable?) : Throwable(message, cause) {
    constructor(message: String): this(message, null)
}

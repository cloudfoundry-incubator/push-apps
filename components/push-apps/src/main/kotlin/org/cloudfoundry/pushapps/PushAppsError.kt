package org.cloudfoundry.pushapps

class PushAppsError(message: String, cause: Throwable?) : Throwable(message, cause) {
    constructor(message: String): this(message, null)
}

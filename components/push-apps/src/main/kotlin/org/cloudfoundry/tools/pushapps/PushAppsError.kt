package org.cloudfoundry.tools.pushapps

class PushAppsError(message: String, cause: Throwable?) : Throwable(message, cause) {
    constructor(message: String): this(message, null)
}

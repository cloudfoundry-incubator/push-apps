package org.cloudfoundry.pushapps

import javax.sql.DataSource

interface DataSourceBuilder {
    var user: String?
    var host: String?
    var databaseName: String?
    var password: String?
    var port: Int

    fun build(): DataSource
}

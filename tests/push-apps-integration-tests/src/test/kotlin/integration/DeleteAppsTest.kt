package integration

import com.nhaarman.mockito_kotlin.argForWhich
import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.pushapps.AppConfig
import org.cloudfoundry.pushapps.DeleteAppConfig
import org.cloudfoundry.pushapps.PushApps
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class DeleteAppsTest : Spek({
    val goodbyeApp = DeleteAppConfig(
        name = "goodbye",
        deleteRoutes = true
    )

    describe("deleting apps") {
        it("deletes all the apps in the config") {
            val tc = buildTestContext(
                apps = emptyList(),
                deleteApps = listOf(goodbyeApp),
                retryCount = 3
            )

            val pushApps = PushApps(
                tc.config,
                tc.cfClientBuilder
            )

            val result = pushApps.pushApps()

            assertThat(result).isTrue()

            verify(tc.cfOperations.applications()).delete(argForWhich {
                name == "goodbye" && deleteRoutes == true
            })
        }
    }
})

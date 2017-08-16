package pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.PushApplicationRequest
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest
import org.cloudfoundry.operations.applications.StartApplicationRequest
import java.io.File
import java.util.concurrent.CompletableFuture

class DeployApplication(
    private val cloudFoundryOperations: CloudFoundryOperations,
    private val appConfig: AppConfig
) {
    fun deploy(): CompletableFuture<Boolean> {
        val pushAppFuture = generatePushAppFuture()

        return pushAppFuture.thenApply {
            generateSetEnvFutures()
        }.thenCompose { setEnvFutures ->
            CompletableFuture.allOf(*setEnvFutures.toTypedArray())
        }.thenCompose {
            generateStartApplicationFuture(appConfig.name)
        }.thenApply {
            true
        }.exceptionally { _ -> false }
    }

    private fun generatePushAppFuture(): CompletableFuture<Void> {
        val pushAppRequest = PushApplicationRequest
            .builder()
            .name(appConfig.name)
            .buildpack(appConfig.buildpack)
            .command(appConfig.command)
            .path(File(appConfig.path).toPath())
            .noStart(true)
            .build()

        return cloudFoundryOperations
            .applications()
            .push(pushAppRequest)
            .toFuture()
    }

    private fun generateSetEnvFutures(): List<CompletableFuture<Void>> {
        val setEnvRequests = generateSetEnvRequests()

        return setEnvRequests.map { request ->
            cloudFoundryOperations
                .applications()
                .setEnvironmentVariable(request)
                .toFuture()
        }
    }

    private fun generateSetEnvRequests(): List<SetEnvironmentVariableApplicationRequest> {
        if (appConfig.environment === null) return emptyList()

        return appConfig.environment.map { variable ->
            SetEnvironmentVariableApplicationRequest
                .builder()
                .name(appConfig.name)
                .variableName(variable.key)
                .variableValue(variable.value)
                .build()
        }
    }

    private fun generateStartApplicationFuture(applicationName: String): CompletableFuture<Void> {
        val startApplicationRequest = StartApplicationRequest.builder().name(applicationName).build()
        return cloudFoundryOperations.applications().start(startApplicationRequest).toFuture()
    }
}
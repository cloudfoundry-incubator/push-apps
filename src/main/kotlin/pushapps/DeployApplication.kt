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
    fun deploy(): CompletableFuture<DeployResult> {
        val pushAppFuture = generatePushAppFuture()

        return pushAppFuture.thenApply {
            generateSetEnvFutures()
        }.thenCompose { setEnvFutures ->
            CompletableFuture.allOf(*setEnvFutures.toTypedArray())
        }.thenCompose {
            generateStartApplicationFuture(appConfig.name)
        }.thenApply {
            DeployResult(
                appName = appConfig.name,
                didSucceed = true
            )
        }.exceptionally { error ->
            DeployResult(
                appName = appConfig.name,
                didSucceed = false,
                error = error
            )
        }
    }

    private fun generatePushAppFuture(): CompletableFuture<Void> {
        var builder = PushApplicationRequest
            .builder()
            .name(appConfig.name)
            .path(File(appConfig.path).toPath())
            .noStart(true)

        builder = setOptionalBuilderParams(builder)

        val pushAppRequest = builder.build()

        return cloudFoundryOperations
            .applications()
            .push(pushAppRequest)
            .toFuture()
    }

    private fun setOptionalBuilderParams(builder: PushApplicationRequest.Builder): PushApplicationRequest.Builder {
        val pushApplicationRequest = builder.build()
        val newBuilder = PushApplicationRequest
            .builder()
            .from(pushApplicationRequest)

        if (appConfig.buildpack !== null) {
            newBuilder.buildpack(appConfig.buildpack)
        }

        if (appConfig.command !== null) {
            newBuilder.command(appConfig.command)
        }

        if (appConfig.instances !== null) {
            newBuilder.instances(appConfig.instances)
        }

        if (appConfig.diskQuota !== null) {
            newBuilder.diskQuota(appConfig.diskQuota)
        }

        if (appConfig.memory !== null) {
            newBuilder.memory(appConfig.memory)
        }

        if (appConfig.noHostname !== null) {
            newBuilder.noHostname(appConfig.noHostname)
        }

        if (appConfig.noRoute !== null) {
            newBuilder.noRoute(appConfig.noRoute)
        }

        return newBuilder
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
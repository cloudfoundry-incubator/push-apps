package pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.PushApplicationRequest
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest
import org.cloudfoundry.operations.applications.StartApplicationRequest
import org.cloudfoundry.operations.applications.StopApplicationRequest
import java.io.File
import java.util.concurrent.CompletableFuture

class DeployApplication(
    private val cloudFoundryOperations: CloudFoundryOperations,
    private val appConfig: AppConfig
) {

    fun deploy(): CompletableFuture<DeployResult> {
        val deployAppFuture = if (appConfig.blueGreenDeploy == true) {
            generateBlueGreenDeployApplicationFuture()
        } else {
            generateDeployApplicationFuture(appConfig.name)
        }

        return deployAppFuture
            .thenApply {
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

    private fun generateBlueGreenDeployApplicationFuture(): CompletableFuture<Void> {
        val blueAppName = appConfig.name + "-blue"
        return generateDeployApplicationFuture(blueAppName)
            .thenCompose { generateDeployApplicationFuture(appConfig.name) }
            .thenCompose { generateStopApplicationFuture(blueAppName) }
    }

    private fun generateStopApplicationFuture(appName: String): CompletableFuture<Void>? {
        val stopApplicationRequest = StopApplicationRequest
            .builder()
            .name(appName)
            .build()

        return cloudFoundryOperations
            .applications()
            .stop(stopApplicationRequest)
            .toFuture()
    }

    private fun generateDeployApplicationFuture(appName: String): CompletableFuture<Void> {
        val pushAppFuture = generatePushAppFuture(appName)

        return pushAppFuture.thenApply {
            generateSetEnvFutures(appName)
        }.thenCompose { setEnvFutures ->
            CompletableFuture.allOf(*setEnvFutures.toTypedArray())
        }.thenCompose {
            generateStartApplicationFuture(appName)
        }
    }

    private fun generatePushAppFuture(appName: String): CompletableFuture<Void> {
        var builder = PushApplicationRequest
            .builder()
            .name(appName)
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

        if (appConfig.timeout !== null) {
            newBuilder.timeout(appConfig.timeout)
        }

        if (appConfig.domain !== null) {
            newBuilder.domain(appConfig.domain)
        }

        return newBuilder
    }

    private fun generateSetEnvFutures(appName: String): List<CompletableFuture<Void>> {
        val setEnvRequests = generateSetEnvRequests(appName)

        return setEnvRequests.map { request ->
            cloudFoundryOperations
                .applications()
                .setEnvironmentVariable(request)
                .toFuture()
        }
    }

    private fun generateSetEnvRequests(appName: String): List<SetEnvironmentVariableApplicationRequest> {
        if (appConfig.environment === null) return emptyList()

        return appConfig.environment.map { variable ->
            SetEnvironmentVariableApplicationRequest
                .builder()
                .name(appName)
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
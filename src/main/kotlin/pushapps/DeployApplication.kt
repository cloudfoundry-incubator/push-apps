package pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.*
import org.cloudfoundry.operations.services.BindServiceInstanceRequest
import reactor.core.publisher.Mono
import java.io.File
import java.util.concurrent.CompletableFuture

class DeployApplication(
    private val cloudFoundryOperations: CloudFoundryOperations,
    private val appConfig: AppConfig
) {

    fun deploy(): CompletableFuture<OperationResult> {
        val deployAppFuture = if (appConfig.blueGreenDeploy == true) {
            generateBlueGreenDeployApplicationFuture()
        } else {
            generateDeployApplicationFuture(appConfig.name)
        }

        return deployAppFuture
            .thenApply {
                OperationResult(
                    name = appConfig.name,
                    didSucceed = true
                )
            }.exceptionally { error ->
            OperationResult(
                name = appConfig.name,
                didSucceed = false,
                error = error
            )
        }
    }

    private fun generateBlueGreenDeployApplicationFuture(): CompletableFuture<Void> {
        val blueAppName = appConfig.name + "-blue"
        return generateDeployApplicationFuture(blueAppName)
            .thenCompose { generateDeployApplicationFuture(appConfig.name) }
            .thenCompose { generateStopApplicationAction(blueAppName).toFuture() }
    }

    private fun generateStopApplicationAction(appName: String): Mono<Void> {
        val stopApplicationRequest = StopApplicationRequest
            .builder()
            .name(appName)
            .build()

        return cloudFoundryOperations
            .applications()
            .stop(stopApplicationRequest)
    }

    private fun generateDeployApplicationFuture(appName: String): CompletableFuture<Void> {
        val pushAppAction = generatePushAppAction(appName)
        val setEnvActions = generateSetEnvActions(appName)
        val bindServiceActions = generateBindServiceActions(appName)

        val pushAppWithEnvFuture = setEnvActions
            .fold(pushAppAction.toFuture()) { acc, setEnvAction ->
                acc.thenCompose { setEnvAction.toFuture() }
            }

        val deployAppFuture = bindServiceActions
            .fold(pushAppWithEnvFuture) { acc, bindServiceAction ->
                acc.thenCompose { bindServiceAction.toFuture() }
            }

        return deployAppFuture.thenCompose { generateStartApplicationAction(appName).toFuture() }
    }

    private fun generatePushAppAction(appName: String): Mono<Void> {
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

        if (appConfig.healthCheckType !== null) {
            newBuilder.healthCheckType(ApplicationHealthCheck.from(appConfig.healthCheckType))
        }

        return newBuilder
    }

    private fun generateSetEnvActions(appName: String): List<Mono<Void>> {
        val setEnvRequests = generateSetEnvRequests(appName)

        return setEnvRequests.map { request ->
            cloudFoundryOperations
                .applications()
                .setEnvironmentVariable(request)
        }
    }

    private fun generateSetEnvRequests(appName: String): Array<SetEnvironmentVariableApplicationRequest> {
        if (appConfig.environment === null) return emptyArray()

        return appConfig.environment.map { variable ->
            SetEnvironmentVariableApplicationRequest
                .builder()
                .name(appName)
                .variableName(variable.key)
                .variableValue(variable.value)
                .build()
        }.toTypedArray()
    }

    private fun generateBindServiceActions(appName: String): List<Mono<Void>> {
        val bindServiceRequests = generateBindServiceRequests(appName)

        return bindServiceRequests.map { request ->
            cloudFoundryOperations
                .services()
                .bind(request)
        }
    }

    private fun generateBindServiceRequests(appName: String): Array<BindServiceInstanceRequest> {
        if (appConfig.serviceNames === null) return emptyArray()

        return appConfig.serviceNames.map { serviceName ->
            BindServiceInstanceRequest
                .builder()
                .applicationName(appName)
                .serviceInstanceName(serviceName)
                .build()
        }.toTypedArray()
    }

    private fun generateStartApplicationAction(applicationName: String): Mono<Void> {
        val startApplicationRequest = StartApplicationRequest.builder().name(applicationName).build()
        return cloudFoundryOperations.applications().start(startApplicationRequest)
    }
}
package org.cloudfoundry.tools.pushapps

import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationHealthCheck
import org.cloudfoundry.operations.applications.PushApplicationRequest
import org.cloudfoundry.tools.pushapps.config.AppConfig
import reactor.core.publisher.Mono
import java.io.File

class PushApplication(
    private val cloudFoundryOperations: CloudFoundryOperations,
    private val appConfig: AppConfig
) {
    fun generatePushAppAction(): Mono<Void> {
        var builder = PushApplicationRequest
            .builder()
            .name(appConfig.name)
            .path(File(appConfig.path).toPath())
            .noRoute(appConfig.noRoute)
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

        if (appConfig.timeout !== null) {
            newBuilder.timeout(appConfig.timeout)
        }

        if (appConfig.domain !== null) {
            newBuilder.domain(appConfig.domain)
        }

        if (appConfig.healthCheckType !== null) {
            newBuilder.healthCheckType(ApplicationHealthCheck.from(appConfig.healthCheckType))
        }

        if (appConfig.stack !== null) {
            newBuilder.stack(appConfig.stack)
        }

        return newBuilder
    }
}

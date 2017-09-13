package pushapps

import org.cloudfoundry.operations.applications.ApplicationHealthCheck
import org.cloudfoundry.operations.applications.PushApplicationRequest
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

class DeployApplication(
    private val cloudFoundryClient: CloudFoundryClient,
    private val appConfig: AppConfig
) {


}
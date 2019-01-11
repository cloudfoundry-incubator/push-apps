package integration

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.cloudfoundry.client.v2.securitygroups.CreateSecurityGroupResponse
import org.cloudfoundry.client.v2.securitygroups.SecurityGroups
import org.cloudfoundry.doppler.LogMessage
import org.cloudfoundry.operations.CloudFoundryOperations
import org.cloudfoundry.operations.DefaultCloudFoundryOperations
import org.cloudfoundry.operations.applications.ApplicationSummary
import org.cloudfoundry.operations.applications.Applications
import org.cloudfoundry.operations.organizations.Organizations
import org.cloudfoundry.operations.routes.Routes
import org.cloudfoundry.operations.services.ServiceInstanceSummary
import org.cloudfoundry.operations.services.Services
import org.cloudfoundry.operations.spaces.SpaceDetail
import org.cloudfoundry.operations.spaces.Spaces
import org.cloudfoundry.operations.stacks.Stacks
import org.cloudfoundry.tools.pushapps.*
import org.cloudfoundry.tools.pushapps.config.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.sql.DataSource

val workingDir = System.getProperty("user.dir")!!

data class IntegrationTestContext(
        val config: Config,
        val cfOperations: DefaultCloudFoundryOperations,
        val cfClientBuilder: CloudFoundryClientBuilder,
        val flyway: FlywayWrapper,
        val dataSourceFactory: DataSourceFactory,
        val dataSource: DataSource
)

fun buildTestContext(
        apps: List<AppConfig> = emptyList(),
        services: List<ServiceConfig> = emptyList(),
        userProvidedServices: List<UserProvidedServiceConfig> = emptyList(),
        migrations: List<Migration> = emptyList(),
        securityGroups: List<SecurityGroup> = emptyList(),
        organization: String = "dewey_decimal",
        space: String = "test",
        retryCount: Int = 0,
        cfOperationTimeoutInMinutes: Long = 1L,
        maxInFlight: Int = 2
): IntegrationTestContext {
    val cfOperations = buildMockCfOperations()
    val cfOperationsBuilder = buildMockCfOperationsBuilder(cfOperations)
    val cfClientBuilder = buildMockCfClientBuilder(cfOperations, cfOperationsBuilder, cfOperationTimeoutInMinutes, retryCount)
    val flyway = buildMockFlywayWrapper()
    val (dataSourceFactory, dataSource) = buildDataSourceFactory()

    val config = createConfig(
        apiHost = "example.com",
        username = "admin",
        password = "password",
        organization = organization,
        space = space,
        apps = apps,
        userProvidedServices = userProvidedServices,
        services = services,
        migrations = migrations,
        securityGroups = securityGroups,
        skipSslValidation = true,
        retryCount = retryCount,
        maxInFlight = maxInFlight,
        cfOperationTimeoutInMinutes = cfOperationTimeoutInMinutes
    )

    return IntegrationTestContext(
        config,
        cfOperations,
        cfClientBuilder,
        flyway,
        dataSourceFactory,
        dataSource
    )
}

private fun buildMockFlywayWrapper(): FlywayWrapper {
    val flywayWrapper = mock<FlywayWrapper>()
    whenever(flywayWrapper.migrate(any(), any(), any(), any())).thenReturn(Mono.empty())
    return flywayWrapper
}

fun buildDataSourceFactory(): Pair<DataSourceFactory, DataSource> {
    val dataSourceFactory = mock<DataSourceFactory>()
    val dataSource = mock<DataSource>()

    whenever(dataSourceFactory.buildDataSource(any())).thenReturn(dataSource)
    whenever(dataSourceFactory.addDatabaseNameToDataSource(any(), any())).thenReturn(dataSource)

    return Pair(dataSourceFactory, dataSource)
}

fun buildMockCfClientBuilder(
    cfOperations: CloudFoundryOperations,
    cfOperationsBuilder: CloudFoundryOperationsBuilder,
    operationTimeoutInMinutes: Long,
    retryCount: Int
): CloudFoundryClientBuilder {
    val cfClientBuilder = mock<CloudFoundryClientBuilder>()
    val cfClient = CloudFoundryClient(
        cloudFoundryOperations = cfOperations,
        cloudFoundryOperationsBuilder = cfOperationsBuilder,
        operationTimeoutInMinutes = operationTimeoutInMinutes,
        retryCount = retryCount
    )

    whenever(cfClientBuilder.build()).thenReturn(cfClient)

    return cfClientBuilder
}

private fun buildMockCfOperationsBuilder(cfOperations: CloudFoundryOperations): CloudFoundryOperationsBuilder {
    val cfOperationsBuilder = mock<CloudFoundryOperationsBuilder>()

    whenever(cfOperationsBuilder.fromExistingOperations(any())).thenReturn(cfOperationsBuilder)
    whenever(cfOperationsBuilder.build()).thenReturn(cfOperations)

    return cfOperationsBuilder
}

private fun buildMockCfOperations(): DefaultCloudFoundryOperations {
    val cfOperations = mock<DefaultCloudFoundryOperations>()
    val cfClient = mock<org.cloudfoundry.client.CloudFoundryClient>()

    val applications = mock<Applications>()
    val organizations = mock<Organizations>()
    val spaces = mock<Spaces>()
    val services = mock<Services>()
    val routes = mock<Routes>()
    val stacks = mock<Stacks>()

    val securityGroups = mock<SecurityGroups>()
    val createSecurityGroupResponse = mock<CreateSecurityGroupResponse>()

    val spaceDetail = mock<SpaceDetail>()
    val serviceInstanceSummary = mock<ServiceInstanceSummary>()
    val applicationSummary = mock<ApplicationSummary>()
    val log = mock<LogMessage>()

    whenever(cfOperations.applications()).thenReturn(applications)
    whenever(cfOperations.organizations()).thenReturn(organizations)
    whenever(cfOperations.spaces()).thenReturn(spaces)
    whenever(cfOperations.services()).thenReturn(services)
    whenever(cfOperations.routes()).thenReturn(routes)
    whenever(cfOperations.stacks()).thenReturn(stacks)
    whenever(cfOperations.cloudFoundryClient).thenReturn(cfClient)

    whenever(cfClient.securityGroups()).thenReturn(securityGroups)

    whenever(securityGroups.create(any())).thenReturn(Mono.just(createSecurityGroupResponse))

    whenever(applications.push(any())).thenReturn(Mono.empty())
    whenever(applications.setEnvironmentVariable(any())).thenReturn(Mono.empty())
    whenever(applications.start(any())).thenReturn(Mono.empty())
    whenever(applications.stop(any())).thenReturn(Mono.empty())
    whenever(applications.logs(any())).thenReturn(Flux.fromIterable(listOf(log)))

    whenever(applications.list()).thenReturn(Flux.fromIterable(emptyList()))

    whenever(applicationSummary.name).thenReturn("Foo Bar App")

    whenever(organizations.list()).thenReturn(Flux.fromIterable(emptyList()))
    whenever(organizations.create(any())).thenReturn(Mono.empty())

    whenever(spaces.list()).thenReturn(Flux.fromIterable(emptyList()))
    whenever(spaces.create(any())).thenReturn(Mono.empty())
    whenever(spaces.get(any())).thenReturn(Mono.just(spaceDetail))

    whenever(spaceDetail.id).thenReturn("abcd-1234")
    whenever(services.listInstances()).thenReturn(Flux.fromIterable(listOf(serviceInstanceSummary)))
    whenever(services.createInstance(any())).thenReturn(Mono.empty())
    whenever(services.createUserProvidedInstance(any())).thenReturn(Mono.empty())
    whenever(services.bind(any())).thenReturn(Mono.empty())

    whenever(serviceInstanceSummary.name).thenReturn("Foo Bar Service")

    whenever(routes.map(any())).thenReturn(Mono.empty())
    whenever(routes.unmap(any())).thenReturn(Mono.empty())

    whenever(stacks.list()).thenReturn(Flux.empty())

    return cfOperations
}

private fun createConfig(
        apiHost: String,
        username: String,
        password: String,
        organization: String,
        space: String,
        apps: List<AppConfig>,
        userProvidedServices: List<UserProvidedServiceConfig>,
        services: List<ServiceConfig>,
        migrations: List<Migration>,
        securityGroups: List<SecurityGroup>,
        skipSslValidation: Boolean,
        retryCount: Int,
        maxInFlight: Int,
        cfOperationTimeoutInMinutes: Long
): Config {
    val cf = CfConfig(
            apiHost = apiHost,
            username = username,
            password = password,
            organization = organization,
            space = space,
            skipSslValidation = skipSslValidation
    )
    return Config(PushAppsConfig(
            operationRetryCount = retryCount,
            maxInFlight = maxInFlight,
            cfOperationTimeoutInMinutes = cfOperationTimeoutInMinutes
    ), cf, apps, services, userProvidedServices, migrations, securityGroups)
}

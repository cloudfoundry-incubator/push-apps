package integration

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.pivotal.pushapps.*
import org.cloudfoundry.client.v2.securitygroups.CreateSecurityGroupResponse
import org.cloudfoundry.client.v2.securitygroups.SecurityGroups
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
import org.flywaydb.core.Flyway
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.sql.DataSource

val workingDir = System.getProperty("user.dir")!!

data class IntegrationTestContext(
    val config: Config,
    val cfOperations: DefaultCloudFoundryOperations,
    val cfClientBuilder: CloudFoundryClientBuilder,
    val flyway: Flyway,
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
    space: String = "test"
): IntegrationTestContext {
    val cfOperations = buildMockCfOperations()
    val cfOperationsBuilder = buildMockCfOperationsBuilder(cfOperations)
    val cfClientBuilder = buildMockCfClientBuilder(cfOperations, cfOperationsBuilder)
    val flyway = buildMockFlyway()
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
        skipSslValidation = true
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

fun buildDataSourceFactory(): Pair<DataSourceFactory, DataSource> {
    val dataSourceFactory = mock<DataSourceFactory>()
    val dataSource = mock<DataSource>()

    whenever(dataSourceFactory.buildDataSource(any())).thenReturn(dataSource)

    return Pair(dataSourceFactory, dataSource)
}

fun buildMockFlyway(): Flyway {
    val flyway = mock<Flyway>()

//    whenever(flyway.setDataSource(any<String>(), any<String>(), any<String>()))
//    whenever(flyway.setLocations(any<String>()))
//    whenever(flyway.migrate())

    return flyway
}

fun buildMockCfClientBuilder(cfOperations: CloudFoundryOperations, cfOperationsBuilder: CloudFoundryOperationsBuilder): CloudFoundryClientBuilder {
    val cfClientBuilder = mock<CloudFoundryClientBuilder>()
    val cfClient = CloudFoundryClient(
        cloudFoundryOperations = cfOperations,
        cloudFoundryOperationsBuilder = cfOperationsBuilder
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

    val securityGroups = mock<SecurityGroups>()
    val createSecurityGroupResponse = mock<CreateSecurityGroupResponse>()

    val spaceDetail = mock<SpaceDetail>()
    val serviceInstanceSummary = mock<ServiceInstanceSummary>()
    val applicationSummary = mock<ApplicationSummary>()

    whenever(cfOperations.applications()).thenReturn(applications)
    whenever(cfOperations.organizations()).thenReturn(organizations)
    whenever(cfOperations.spaces()).thenReturn(spaces)
    whenever(cfOperations.services()).thenReturn(services)
    whenever(cfOperations.routes()).thenReturn(routes)
    whenever(cfOperations.cloudFoundryClient).thenReturn(cfClient)

    whenever(cfClient.securityGroups()).thenReturn(securityGroups)

    whenever(securityGroups.create(any())).thenReturn(Mono.just(createSecurityGroupResponse))

    whenever(applications.push(any())).thenReturn(Mono.empty())
    whenever(applications.setEnvironmentVariable(any())).thenReturn(Mono.empty())
    whenever(applications.start(any())).thenReturn(Mono.empty())
    whenever(applications.stop(any())).thenReturn(Mono.empty())

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
    migrations: List<Migration>?,
    securityGroups: List<SecurityGroup>?,
    skipSslValidation: Boolean
): Config {
    val cf = CfConfig(apiHost, username, password, organization, space, skipSslValidation)
    return Config(PushAppsConfig(), cf, apps, services, userProvidedServices, migrations, securityGroups)
}

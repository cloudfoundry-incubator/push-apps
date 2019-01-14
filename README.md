# Push Apps

A jar that pushes apps to CF for PCF Metrics. It is configured using a YAML configuration file, and is designed to make
it easy to push a suite of apps and set up related services and security groups as well as running migrations. It was
originally developed by the PCF Metrics team.

## Usage

Call the jar with a `-c` argument specifying the location of the config file.

```bash
java -jar "applications/cli/build/libs/push-apps-${version}.jar" -c config.yml
```

## Configuration

Push Apps is configured using a YAML configuration file. Below is an example.
We will try to keep this up to date, but just in case, you should be able to use
the types in the classes within [`pushapps/config`](components/push-apps/src/main/kotlin/org/cloudfoundry/tools/pushapps/config)
to determine which types are expected for each field, and which are and are not required.

```yaml
pushApps: #required hash
  operationRetryCount: 3 # optional, default 0
  maxInFlight: 4 #optional, default 2
  failedDeploymentLogLinesToShow: 50 #optional, default 50
  migrationTimeoutInMinutes: 7 #optional, default 15
  cfOperationTimeoutInMinutes: 1 #optional, default 5
cf: #required hash
  apiHost: api.cf.example.com #required
  username: admin #required
  password: secret #required
  organization: dewey #required
  space: outer #required
  skipSslValidation: true #optional, default false
  dialTimeoutInMillis: #optional, CF default
apps: #required array
- name: backend #required
  path: "/var/vcap/packages/metrics-data/backend.zip" #required
  buildpack: binary_buildpack #optional, CF default
  healthCheckType: none #optional, CF default
  instances: 2 #optional, CF default
  memory: 4096M #optional, CF default
  stackPriority:
  - cflinuxfs3
  - cflinuxfs2 #optional, will use highest priority available stack
  diskQuota: 1G #optional, CF default (eg: 500M, 2G)
  noRoute: true #optional, CF default
  blueGreenDeploy: true #optional, default false
  command: "./backend/backend" #optional, CF default
  domain: "cfapps.example.com" #optional, required if route is provided
  timeout: 180 #optional, CF default
  environment: #optional hash
    MYSQL_HOSTS: 127.0.0.1
    MYSQL_USER: admin
    MYSQL_PASSWORD: secret
    MYSQL_DB: backend
  serviceNames: #optional array
  - "metrics_forwarder"
- name: frontend #required
  path: "/var/vcap/packages/metrics-data/frontend.zip" #required
  buildpack: binary_buildpack #optional, CF default
  healthCheckType: none #optional, CF default
  instances: 2 #optional, CF default
  memory: 4G #optional, CF default
  stackPriority:
  - cflinuxfs3
  - cflinuxfs2 #optional, will use highest priority available stack
  diskQuota: 1G #optional, CF default (eg: 500M, 2G)
  noRoute: true #optional, CF default
  blueGreenDeploy: true #optional, default false
  command: "./frontend/frontend" #optional, CF default
  domain: "cfapps.example.com" #optional, required if route is provided, required if route is provided
  timeout: 180 #optional, CF default
  route: #optional hash
    hostname: some-app
    path: /api/v1 #optional, default null
  environment: #optional hash
    MYSQL_HOSTS: 127.0.0.1
    MYSQL_USER: admin
    MYSQL_PASSWORD: secret
    MYSQL_DB: frontend
  serviceNames: #optional array
  - "metrics_forwarder"
services: #optional array
- name: "metrics_forwarder" #required
  plan: "4x4000" #required
  broker: "custom_metrics" #required
  optional: true #optional, default false
userProvidedServices: #optional array
- name: "elasticsearch" #required
  credentials: #required hash
    host: 127.0.0.1
migrations: #optional array
- driver: mysql #required, supports mysql or postgres
  user: admin #required
  password: secret #required
  host: 127.0.0.1 #required
  port: 3306 #required
  schema: backend #required
  migrationDir: /var/vcap/packages/backend/dbmigrations/metrics/ #required
  repair: true #optional, default false
securityGroups: #optional array
- name: outer-api #required
  destination: "0.0.0.0-255.255.255.255" #required
  protocol: all #required
```

## Development and Debugging

A few notes to help people contribute to this project.

* The [bin](bin) directory contains a couple helpful scripts. `pushApps.sh` will build the
latest version and call the jar, so you can use it like `bin/pushApps.sh -c config.yml`.
`pushAppsDebug.sh` does the same thing, but also attaches to a remote debugger. This is
helpful in debugging problems. Make sure to spin up a remote debugger in Idea for running
this script.
* There is an [End to End Acceptance Test](applications/acceptance-tests/src/test/kotlin/acceptance/EndToEndAcceptanceTest.kt)
that will actually hit a real Cloud Foundry instance. The test will looking for the following environment
variables to determine which environment to hit.

    ```
    CF_API=api.cf.example.com
    CF_USERNAME=admin
    CF_PASSWORD=secret
    CF_DOMAIN=cf.example.com
    ```

This test does not run as a part of the build `./gradlew build`, but will run on CI, and is available via the
`acceptanceTest` gradle task.

### CI

CI is running on [concourse](https://concourse.superpipe.gcp.pcf-metrics.com/teams/main/pipelines/push-apps).

Pushing this repository will trigger a build. 

In order to update the pipelines, you must have lpass and superpipe access and you can run `ci/set-pipeline.sh`. 
Any changes to `ci/push-app.yml` should be pushed up to github once you are happy with the pipeline.

Currently, the pipeline is targeted to a denver bbl deployed environment (see `CF_DOMAIN` in the pipeline). 

## Known Issues

The following issues are known and have stories/bugs waiting for them in the backlog.

* Push Apps will automatically retry on 502 errors from CF. There is no limit to the number
of times it will retry, nor is exponential back-off implemented yet.
* There is a bug that causes null integer values (ie. optional integer values not provided in the
config) to be read in as 0s instead of getting the appropriate default.
* If one DB migration fails, Push Apps will not run remaining ones.

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

Push Apps is configured using a YAML configuration file. Below is an example,
we will try to keep this up to date, but just in case, you should be able to use
the types is the [ConfigReader.kt](components/push-apps/src/main/kotlin/io/pivotal/pushapps/ConfigReader.kt) 
class to determine the type it is expecting for each field, and which are and are not required.

```yaml
pushApps: #required hash
  appDeployRetryCount: 3 # optional, default 0
  maxInFlight: 4 #optional, default 2
  failedDeploymentLogLinesToShow: 50 #optional, default 50
cf: #required hash
  apiHost: api.cf.example.com #required
  username: admin #required
  password: secret #required
  organization: dewey #required
  space: outer #required
  skipSslValidation: true #optional, default false
  dialTimeoutInMillis: #optional
apps: #required array
- name: backend #required
  path: "/var/vcap/packages/metrics-data/backend.zip" #required
  buildpack: binary_buildpack #optional
  healthCheckType: none #optional
  instances: 2 #optional
  memory: 4096 #optional
  noRoute: true #optional
  blueGreenDeploy: true #optional
  command: "./backend/backend" #optional
  domain: "cfapps.example.com" #optional
  timeout: 180 #optional
  environment: #optional hash
    MYSQL_HOSTS: 127.0.0.1
    MYSQL_USER: admin
    MYSQL_PASSWORD: secret
    MYSQL_DB: backend
  serviceNames: #optional array
  - "metrics_forwarder"
- name: frontent #required
  path: "/var/vcap/packages/metrics-data/frontend.zip" #required
  buildpack: binary_buildpack #optional
  healthCheckType: none #optional
  instances: 2 #optional
  memory: 4096 #optional
  noRoute: true #optional
  blueGreenDeploy: true #optional
  command: "./frontend/frontend" #optional
  domain: "cfapps.example.com" #optional
  timeout: 180 #optional
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
  repair: true #required
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
that will actually hit a real Cloud Foundry instance. The instance it will hit is based off the
values you put in your `.envrc` (see an example below). This test does not run as a part of
the build `./gradlew build`, but will run on CI, and is availabe via the `acceptanceTest` gradle
task.
    
    ```
    export CF_API=api.cf.example.com
    export CF_USERNAME=admin
    export CF_PASSWORD=secret
    ```

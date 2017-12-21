 # Example build.gradle files for using this library
This library is published to jcenter and maven central. These `build.gradle` files should
get you started using it.

## Downloading the JAR

The `copyDeps` task of this `build.gradle` file will fetch the standalone jar
and copy it to the `libs` directory of the build folder.

```groovy
apply plugin: 'java'

repositories {
  jcenter()
  maven {
    url "https://dl.bintray.com/trevorwhitney/maven"
  }
}

dependencies {
  compile('org.cloudfoundry.tools:push-apps-standalone:0.0.45')
}

task wrapper(type: Wrapper) {
  gradleVersion = "4.4"
}

task copyDeps(type: Copy) {
  from configurations.runtime
  into "$buildDir/libs"
}
```

## Using Push Apps

This `build.gradle` will get you started using Push Apps.

```groovy
apply plugin: 'java'

repositories {
  jcenter()
  maven {
    url "https://dl.bintray.com/trevorwhitney/maven"
  }
}

dependencies {
  compile('org.cloudfoundry.tools:push-apps:0.0.45')
}

task wrapper(type: Wrapper) {
  gradleVersion = "4.4"
}
```

### TODO:

- Sources don't seem to be downloading correctly with the Push Apps jar

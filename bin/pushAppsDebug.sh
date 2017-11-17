#!/bin/bash

./gradlew assemble 2>&1 > /dev/null
version=`./gradlew version | grep ^[0-9]`
java -agentlib:jdwp=transport=dt_socket,server=n,address=127.0.0.1:5005,suspend=y -jar "applications/cli/build/libs/push-apps-${version}.jar" "${@}"

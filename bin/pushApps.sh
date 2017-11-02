#!/bin/bash

./gradlew assemble 2>&1 > /dev/null
version=`./gradlew version | grep ^[0-9]`
java -jar "build/libs/push-apps-${version}.jar" "${@}"

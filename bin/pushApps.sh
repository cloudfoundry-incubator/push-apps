#!/bin/bash

./gradlew assemble 2>&1 > /dev/null
java -jar build/libs/push-apps.jar "$@"

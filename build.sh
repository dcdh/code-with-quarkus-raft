#!/bin/bash
# mvn clean install -DskipTests
mvn clean install -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true

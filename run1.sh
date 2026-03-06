#!/bin/bash
java -Draft.node-id=n1 -Draft.port=6001 -Draft.peers=http://localhost:6001,http://localhost:6002,http://localhost:6003 -jar target/quarkus-app/quarkus-run.jar
#target/code-with-quarkus-raft-1.0.0-SNAPSHOT-runner -Draft.node-id=n1 -Draft.port=6001 -Draft.peers=http://localhost:6001,http://localhost:6002,http://localhost:6003

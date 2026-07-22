#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"

mvn -q \
	-pl indexer-dependencies,indexer-parent,indexer-node-application \
	-am \
	-DskipTests \
	install
mvn -q -f indexer-node-application/pom.xml -DskipTests jib:dockerBuild
exec docker compose up --remove-orphans

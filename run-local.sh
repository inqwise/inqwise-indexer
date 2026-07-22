#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"

mvn -q -pl indexer -am -DskipTests package
exec java -jar indexer/target/indexer-local.jar

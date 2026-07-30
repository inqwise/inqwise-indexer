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

clustered=${INDEXER_CLUSTERED:-}
if [ -z "$clustered" ] && [ -f "$project_dir/.env" ]; then
	clustered=$(sed -n 's/^INDEXER_CLUSTERED=//p' "$project_dir/.env" | tail -n 1)
fi

case "${clustered:-false}" in
	true)
		exec docker compose \
			-f compose.yaml \
			-f compose.cluster.yaml \
			up \
			--remove-orphans
		;;
	false)
		exec docker compose \
			-f compose.yaml \
			up \
			--remove-orphans
		;;
	*)
		echo "INDEXER_CLUSTERED must be true or false" >&2
		exit 2
		;;
esac

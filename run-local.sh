#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"
. "$project_dir/deployment/local/cluster-address.sh"

mvn -q \
	-pl indexer-dependencies,indexer-parent,indexer-node-application \
	-am \
	-DskipTests \
	install
mvn -q -f indexer-node-application/pom.xml -DskipTests jib:dockerBuild

env_file="$project_dir/.env"
clustered=$(resolve_indexer_setting "${INDEXER_CLUSTERED:-}" "$env_file" INDEXER_CLUSTERED)

case "${clustered:-false}" in
	true)
		public_host=$(resolve_indexer_setting "${INDEXER_PUBLIC_HOST:-}" "$env_file" INDEXER_PUBLIC_HOST)
		if [ -z "$public_host" ]; then
			public_host=$(detect_indexer_public_host)
		fi
		hazelcast_forward_port=$(resolve_indexer_setting "${INDEXER_HAZELCAST_FORWARD_PORT:-}" "$env_file" INDEXER_HAZELCAST_FORWARD_PORT)
		hazelcast_forward_port=${hazelcast_forward_port:-5702}
		cluster_port=$(resolve_indexer_setting "${INDEXER_CLUSTER_PORT:-}" "$env_file" INDEXER_CLUSTER_PORT)
		cluster_port=${cluster_port:-15701}
		hazelcast_public_address=$(resolve_indexer_setting "${INDEXER_HAZELCAST_PUBLIC_ADDRESS:-}" "$env_file" INDEXER_HAZELCAST_PUBLIC_ADDRESS)
		hazelcast_forward_host=$(resolve_indexer_setting "${INDEXER_HAZELCAST_FORWARD_HOST:-}" "$env_file" INDEXER_HAZELCAST_FORWARD_HOST)
		cluster_public_host=$(resolve_indexer_setting "${INDEXER_CLUSTER_PUBLIC_HOST:-}" "$env_file" INDEXER_CLUSTER_PUBLIC_HOST)
		cluster_public_port=$(resolve_indexer_setting "${INDEXER_CLUSTER_PUBLIC_PORT:-}" "$env_file" INDEXER_CLUSTER_PUBLIC_PORT)
		cluster_forward_host=$(resolve_indexer_setting "${INDEXER_CLUSTER_FORWARD_HOST:-}" "$env_file" INDEXER_CLUSTER_FORWARD_HOST)
		cluster_forward_port=$(resolve_indexer_setting "${INDEXER_CLUSTER_FORWARD_PORT:-}" "$env_file" INDEXER_CLUSTER_FORWARD_PORT)
		export INDEXER_HAZELCAST_PUBLIC_ADDRESS=${hazelcast_public_address:-${public_host}:${hazelcast_forward_port}}
		export INDEXER_HAZELCAST_FORWARD_HOST=${hazelcast_forward_host:-$public_host}
		export INDEXER_HAZELCAST_FORWARD_PORT=$hazelcast_forward_port
		export INDEXER_CLUSTER_PUBLIC_HOST=${cluster_public_host:-$public_host}
		export INDEXER_CLUSTER_PUBLIC_PORT=${cluster_public_port:-$cluster_port}
		export INDEXER_CLUSTER_FORWARD_HOST=${cluster_forward_host:-$public_host}
		export INDEXER_CLUSTER_FORWARD_PORT=${cluster_forward_port:-$cluster_port}
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

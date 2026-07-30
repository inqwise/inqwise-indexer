#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"

node_host="${INDEXER_NODE_HOST:-192.168.6.171}"
hazelcast_port="${INDEXER_HAZELCAST_PUBLIC_PORT:-5702}"
cluster_name="${INDEXER_CLUSTER_NAME:-inqwise-indexer-local}"

exec mvn -pl indexer-example-hacker-news \
	-Dvertx.hazelcast.config="$project_dir/deployment/local/hazelcast.xml" \
	-Dindexer.cluster.name="$cluster_name" \
	-Dindexer.cluster.members="${node_host}:${hazelcast_port}" \
	-Dhazelcast.local.localAddress="$node_host" \
	org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
	-Dexec.mainClass=io.vertx.launcher.application.VertxApplication \
	-Dexec.args="com.inqwise.indexer.example.hn.HackerNewsApplicationVerticle --cluster --cluster-host ${node_host} --conf ${project_dir}/deployment/local/hacker-news.json"

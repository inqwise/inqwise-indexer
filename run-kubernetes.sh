#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"

examples="${INDEXER_KUBERNETES_EXAMPLES:-true}"
build_images="${INDEXER_KUBERNETES_BUILD_IMAGES:-true}"
wait_for_rollout="${INDEXER_KUBERNETES_WAIT:-true}"

validate_boolean() {
	local name="$1"
	local value="$2"

	case "$value" in
		true|false)
			;;
		*)
			echo "$name must be true or false" >&2
			exit 2
			;;
	esac
}

validate_boolean INDEXER_KUBERNETES_EXAMPLES "$examples"
validate_boolean INDEXER_KUBERNETES_BUILD_IMAGES "$build_images"
validate_boolean INDEXER_KUBERNETES_WAIT "$wait_for_rollout"

case "$examples" in
	true)
		module="indexer-example-hacker-news-node-application"
		manifest_dir="deployment/kubernetes"
		;;
	false)
		module="indexer-node-application"
		manifest_dir="deployment/kubernetes/overlays/core-only"
		;;
esac

case "$build_images" in
	true)
		mvn -q -pl "$module" -am -DskipTests install
		mvn -q -f "$module/pom.xml" -DskipTests jib:dockerBuild
		;;
	false)
		;;
esac

kubectl apply -k "$manifest_dir"

case "$wait_for_rollout" in
	true)
		kubectl -n inqwise-indexer rollout status deployment/indexer-node --timeout=180s
		;;
	false)
		;;
esac

# Kubernetes Deployment

This directory provides a Kustomize-based evaluation deployment for the
Indexer node.

## Variants

The root configuration enables examples and deploys the combined Hacker News
consumer distribution:

```sh
kubectl apply -k deployment/kubernetes
```

The core-only overlay deploys the generic node without consumer examples:

```sh
kubectl apply -k deployment/kubernetes/overlays/core-only
```

`run-kubernetes.sh` selects the same variants through
`INDEXER_KUBERNETES_EXAMPLES`, which defaults to `true`.

## Images

By default, the script builds the selected image with Jib into the local Docker
daemon. This works directly when the Kubernetes cluster uses that daemon, such
as Docker Desktop Kubernetes. Kind, Minikube, and remote clusters require the
image to be loaded or published through their normal image workflow. After the
image is available, run with `INDEXER_KUBERNETES_BUILD_IMAGES=false`.

The manifests expect these image names:

- Example-enabled: `inqwise/indexer-node-hacker-news:0.1.0-SNAPSHOT`
- Core-only: `inqwise/indexer-node:0.1.0-SNAPSHOT`

Use a Kustomize image override in a deployment-owned overlay when publishing to
a registry. Do not edit the base to encode an environment-specific registry.

## Runtime Shape

The deployment creates:

- Namespace `inqwise-indexer`.
- One `indexer-node` Pod with startup, readiness, and liveness probes.
- ClusterIP Service `indexer-node` for the console and internal HTTP ports.
- Headless Service `indexer-node-headless` for Hazelcast and Vert.x discovery.
- ConfigMaps for node, Hazelcast, and Vert.x configuration.

The Pod advertises its own IP for Hazelcast and the clustered Vert.x EventBus.
The headless Service supplies peer DNS, so the Pod does not read the Kubernetes
API and does not require a ServiceAccount or RBAC permissions.

The current repositories, queues, document storage, and query provider are
in-memory. The Deployment therefore fixes replicas to one and uses `Recreate`.
This is an evaluation environment, not a highly available or production
topology.

ConfigMaps are mounted with `subPath`; restart the Deployment after changing
configuration:

```sh
kubectl -n inqwise-indexer rollout restart deployment/indexer-node
```

## Local Access

Forward the operator endpoints when the cluster has no ingress:

```sh
kubectl -n inqwise-indexer port-forward service/indexer-node \
	3000:3000 8080:8080 8081:8081 8083:8083 8084:8084 8086:8086 9090:9090
```

The Hacker News example requires outbound HTTPS access from the Pod.

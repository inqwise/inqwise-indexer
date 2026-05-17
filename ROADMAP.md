# Roadmap

## Index Flow

- Design the concrete queue/resource provisioning contract for indexer creation. In particular, decide whether `queueName` is always derived from `indexName`, stored explicitly on `IndexerModel`, or versioned for replacement/preload flows.
- Add durable command state storage for `SubmitIndexActionsCommand` when the command service moves beyond the in-memory implementation. The intended states are received, ensuring indexer, publish-ready, publishing, published, retryable failure, and final failure.
- Define batch/action idempotency for publish retries. A publish timeout may mean that actions were already accepted by the queue, so retries need stable `batchId` or action ids and downstream duplicate tolerance.
- Decide how publish-ready checks should verify external resources once real queue/topic and document-index clients are introduced.

## Document Store Publishing

- Add repository-backed mutation tracking only for the blend window where historical reload data is mixed with live stream mutations.
- Decide the query-side contract for resolving multiple published indexers by `targetId`, including ordering and conflict behavior when a target has more than one published physical index.
- Define `ReloadIndexer` blend mode for concurrent historical snapshot load plus live stream. This includes stale-write protection, a hot-path mutation-state store or document-store conditional writes, and indexer-scoped mutation-state cleanup after the blend window closes.
- Deferred: back the id-first `DocumentStoreMetadataRepository` with the production storage engine and preserve the insert/update/delete model split used by the in-memory implementation.

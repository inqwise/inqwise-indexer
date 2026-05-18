# Roadmap

## Index Flow

- Add public API error mapping so internal ids, index names, queue names, and storage details are not exposed directly to external callers.
- Add authorization, ownership checks, and rate limits before exposing auto-provision-on-write to public API traffic.
- Design the concrete queue/resource provisioning contract for indexer creation. In particular, decide whether `queueName` is always derived from `indexName`, stored explicitly on `IndexerModel`, or versioned for replacement/preload flows.
- Add durable command state storage for `SubmitIndexActionsCommand` when the command service moves beyond the in-memory implementation. The intended states are received, ensuring indexer, publish-ready, publishing, published, retryable failure, and final failure.
- Define batch/action idempotency for publish retries. A publish timeout may mean that actions were already accepted by the queue, so retries need stable `batchId` or action ids and downstream duplicate tolerance.
- Decide how publish-ready checks should verify external resources once real queue/topic and document-index clients are introduced.
- Add an explicit retry/recovery command for concrete targets whose first writable indexer provisioning failed.
- Consider wrapping external queue/topic and document-index provisioning calls with Vert.x Circuit Breaker. Do not encode circuit-breaker behavior in the metadata model.

## Document Store Publishing

- Add query API support for resolving `targetUid` or `targetName` plus a time range into published physical `indexName` values. Queries should skip missing period targets and return empty results when no published indexes exist.
- Add target-definition-level control for concrete target auto-creation. The current behavior auto-creates concrete targets on first write.
- Add repository-backed mutation tracking only for the blend window where historical reload data is mixed with live stream mutations.
- Decide the query-side contract for resolving multiple published indexers by `targetId`, including ordering and conflict behavior when a target has more than one published physical index.
- Define `ReloadIndexer` blend mode for concurrent historical snapshot load plus live stream. This includes stale-write protection, a hot-path mutation-state store or document-store conditional writes, and indexer-scoped mutation-state cleanup after the blend window closes.
- Deferred: back the id-first `DocumentStoreMetadataRepository` with the production storage engine and preserve the insert/update/delete model split used by the in-memory implementation.

## Runtime Resources

- Define production `IndexerQueueResourceCleaner` behavior for idempotent Kafka topic cleanup.
- Define production document-index cleanup behavior through `IndexerResourceCleaner`.

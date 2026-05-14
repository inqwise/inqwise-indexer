# Roadmap

## Index Flow

- Design the concrete queue/resource provisioning contract for indexer creation. In particular, decide whether `queueName` is always derived from `indexName`, stored explicitly on `IndexerModel`, or versioned for replacement/preload flows.
- Add durable command state storage for `SubmitIndexActionsCommand` when the command service moves beyond the in-memory implementation. The intended states are received, ensuring indexer, publish-ready, publishing, published, retryable failure, and final failure.
- Define batch/action idempotency for publish retries. A publish timeout may mean that actions were already accepted by the queue, so retries need stable `batchId` or action ids and downstream duplicate tolerance.
- Decide how publish-ready checks should verify external resources once real queue/topic and document-index clients are introduced.

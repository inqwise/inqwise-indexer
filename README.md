# inqwise-indexer

Vert.x 5.x starter library inspired by `vertx-elastic`, with a modular layout:

- `inqwise-common`: shared data objects and indexing request models.
- `inqwise-indexer`: indexing service contracts and default in-memory implementation.
- `inqwise-client`: client-side wrappers for the indexer service.
- `inqwise`: top-level facade for index actions and preload orchestration.

## Indexer Behavior

`inqwise-indexer` provides a controlled transport pipeline for gently moving structured actions into a targeted document store:

- `Indexer`: root runtime component that activates a queue consumer, receives `IndexerActionItem` portions, pauses the consumer while processing, commits successful portions, resumes consumption, and emits transport events.
- `IndexerQueue`: buffer abstraction. Production implementations can use Kafka or another durable transport. `InMemoryIndexerQueue` is a simple local/test implementation.
- `IndexerQueueConsumer`: consumer side of the queue. It owns bulk/portion delivery policy, exposes `pause`, `resume`, `commit`, and `close`, and calls the configured item handler.
- `IndexerActionItem`: abstract action payload. `PutDocumentActionItem` writes a document to a concrete `indexName`; `CompleteIndexActionItem` marks the action stream as complete.
- `IndexerDocumentStore`: target document-store abstraction. The default document store is in-memory.
- `IndexerRepository`: persistence abstraction for `IndexerModel` records. The default repository is in-memory.

### Index Flow

Indexing uses two paths:

- Hot path: if the target indexer is already known to be publish-ready, callers publish `IndexerActionItem` payloads directly to `IndexerQueue`.
- Cold/unknown path: callers submit `SubmitIndexActionsCommand`. The command handler loads or creates the `IndexerModel`, verifies that the indexer is publish-ready, publishes a lifecycle wake-up, then publishes the submitted actions to `IndexerQueue`.

Command completion means that the submitted actions were handed to the indexer queue, not that the documents were already indexed. Runtime processing remains asynchronous.

The cold path fails closed. If the command sees an unexpected indexer state or action mismatch, it must not publish actions. Expected/idempotent cases include creating a missing model, reusing an active model, and waking runtime consumers that may not be hot yet.

The current fail-closed guards are:

- a deleted indexer cannot receive new actions;
- a non-active indexer cannot receive new actions;
- multiple repository records for the same `indexName` are treated as an unexpected state;
- `PUT_DOCUMENT` actions must target the command `indexName`;
- `REMOVE_DOCUMENT` actions with a `targetName` must match the command `targetName` when both are present.

### Lifecycle

- `activate()`: starts the root indexer once. It activates the queue consumer/listener, resumes consumption, and emits `INDEXER_STARTED`. Repeated calls are idempotent.
- `unregister()`: closes the active consumer/listener. It may inspect `nextIndexer`, but an active consumer on `nextIndexer` is unexpected because chained indexers are not roots by definition.
- `close()`: stronger runtime cleanup. It should close the current consumer/listener and the referenced queue, including publish and consume sides.
- `delete()`: deletes the current indexer and its own referenced resources only. It must not delete `nextIndexer`.

`nextIndexer` represents a chained/replacement indexer, not another root consumer. If `nextIndexer` is listening to a queue consumer, that should be treated as unexpected behavior and surfaced before the delete/unregister flow is finalized.

### Distributed Lifecycle Commands

Lifecycle commands express durable desired state. `ActivateIndexerCommand` and `DeactivateIndexerCommand` are handled through the generic `CommandService` layer. Their handlers update `IndexerRepository` status/version and publish an `IndexerLifecycleChanged` notification with the indexer id, command type, and resulting version.

The lifecycle notification is a fan-out wake-up for runtime nodes, not the source of truth. `IndexerRuntime` subscribes to lifecycle changes, reloads the latest `IndexerModel` identified by the event, and reconciles local runtime resources from that model. Production implementations should back `IndexerLifecycleEventBus` with a durable pub/sub topic. The in-memory implementation retains events and replays them to late subscribers for local tests.

## Preload Flow

Creating an indexer with `IndexerType.PRELOAD` returns an `IndexerCreateResult` with `preloadAddress`.
Send a `JsonArray` of `IndexerActionItem.toJson()` payloads to that address. Add the `PreloadIndexer.LAST_HEADER` header to the final message to complete preload and promote the replacement indexer.

## Build

```bash
mvn clean test
```

## Minimal usage

```java
Vertx vertx = Vertx.vertx();
IndexerService indexer = new DefaultIndexerService(vertx);
InqwiseIndexerService inqwise = new DefaultInqwiseIndexerService(indexer);

PutDocumentActionItem request = PutDocumentActionItem.builder()
  .withIndexName("customers_1")
  .withUid("42")
  .withDocument(new JsonObject().put("name", "Ada"))
  .build();

inqwise.indexAction(request);
```

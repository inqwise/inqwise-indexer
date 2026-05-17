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
- `DocumentStoreMetadataRepository`: id-first metadata abstraction for targets, indexers, publications, manifests, and mutation state. The default repository is in-memory.

### Document Store Publishing Model

Document-store publishing separates logical routing from physical index execution:

- `targetName` is the logical business target, period, or collection group, such as `customers-2024`.
- `indexName` is the concrete physical document-store index, such as `customers-2024-01J...`.
- the indexer id or uid identifies one durable physical index version.
- `PUBLISHED` means queryable; it does not mean immutable.
- publication state and mutation state are separate, so a valid physical index can be `PUBLISHED` and `WRITABLE`.

Repository access for document-store metadata is id-first. Identity lookup uses `id` or `uid`, and relationship lookup uses foreign ids such as `targetId` and `indexerId`. Names remain stored for validation, display, and physical execution, but they are not the default repository access path.

Logical write requests route by `targetId`. Command orchestration resolves writable indexers for each target, expands logical mutations to concrete `IndexerActionItem` payloads, and publishes those concrete actions to each resolved indexer queue. Runtime action items execute by `indexerId` and `indexName`; `Indexer` must stay focused on concrete runtime transport and document-store writes.

Query routing resolves published indexers by `targetId` and queries only the resulting concrete `indexName` values. A target may have zero published indexes during first build, multiple writable indexes during rebuild, and multiple published indexes when the query model requires it.

### Index Flow

Indexing uses two paths:

- Hot path: if the target indexer is already known to be publish-ready, callers publish `IndexerActionItem` payloads directly to `IndexerQueue`.
- Cold/unknown path: callers submit `SubmitIndexActionsCommand`. The command handler resolves writable metadata indexers by `targetId`, verifies each destination, expands logical actions to concrete `indexerId`/`indexName` payloads, publishes a lifecycle wake-up, then publishes the concrete actions to scoped `IndexerQueuePublisher` endpoints.

Command completion means that the submitted actions were handed to the indexer queue, not that the documents were already indexed. Runtime processing remains asynchronous.

The cold path fails closed. If the command sees an unexpected indexer state or action mismatch, it must not publish actions. Expected/idempotent cases include resolving a writable active metadata indexer and waking runtime consumers that may not be hot yet.

The current fail-closed guards are:

- a deleted indexer cannot receive new actions;
- a non-active indexer cannot receive new actions;
- logical actions must carry `targetId` so writable indexers can be resolved;
- concrete actions with `indexerId` and `indexName` must match the resolved metadata indexer.

### Lifecycle

- `activate()`: starts the root indexer once. It activates the queue consumer/listener, resumes consumption, and emits `INDEXER_STARTED`. Repeated calls are idempotent.
- `unregister()`: closes the active consumer/listener. It may inspect `nextIndexer`, but an active consumer on `nextIndexer` is unexpected because chained indexers are not roots by definition.
- `close()`: stronger runtime cleanup. It should close the current consumer/listener and the referenced queue, including publish and consume sides.
- `delete()`: deletes the current indexer and its own referenced resources only. It must not delete `nextIndexer`.

`nextIndexer` represents a chained/replacement indexer, not another root consumer. If `nextIndexer` is listening to a queue consumer, that should be treated as unexpected behavior and surfaced before the delete/unregister flow is finalized.

### Distributed Lifecycle Commands

Lifecycle commands express durable desired state. `ActivateIndexerCommand` and `DeactivateIndexerCommand` are handled through the generic `CommandService` layer. Their handlers update `DocumentStoreMetadataRepository` runtime status/version and publish an `IndexerLifecycleChanged` notification with the indexer id, command type, and resulting version.

The lifecycle notification is a fan-out wake-up for runtime nodes, not the source of truth. `IndexerRuntime` subscribes to lifecycle changes, reloads the latest metadata indexer identified by the event, maps it to an `IndexerModel` for runtime transport, and reconciles local resources from that model. Production implementations should back `IndexerLifecycleEventBus` with a durable pub/sub topic. The in-memory implementation retains events and replays them to late subscribers for local tests.

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

# inqwise-indexer

Vert.x 5.x starter library inspired by `vertx-elastic`, with a modular layout:

- `inqwise-common`: shared data objects and indexing request models.
- `inqwise-indexer`: indexing service contracts and default in-memory implementation.
- `inqwise-client`: client-side wrappers for the indexer service.
- `inqwise`: top-level facade for index actions and preload orchestration.

## Indexer Behavior

`inqwise-indexer` provides a controlled transport pipeline for gently moving structured actions into a targeted document store:

- `Indexer`: front object for one internal indexer. It owns producer-side submission into the indexer queue, validates concrete action identity, delegates action-specific document writes to `IndexerAction`, and delegates consumer lifecycle to an `IndexerProcessor` when one is provided.
- `IndexerProcessor`: consumer-side runtime abstraction. `VerticleIndexerProcessor` deploys an `IndexerProcessorVerticle` and hides the Vert.x deployment id.
- `IndexerProcessorVerticle`: owns queue consumption for one indexer, including pause, process, commit, and resume. It receives only an `ActionItemProcessHandler`, not the whole `Indexer`.
- `IndexerQueueClient`: buffer client abstraction for publisher and consumer handles. Production implementations can use Kafka or another durable transport. `InMemoryIndexerQueue` is a simple local/test implementation.
- `IndexerQueueResourceManager`: admin-side abstraction for ensuring and deleting queue resources. Queue provisioning and deletion are not part of the shared runtime queue client surface.
- `IndexerQueueConsumer`: consumer side of the queue. It owns bulk/portion delivery policy, exposes `pause`, `resume`, `commit`, and `close`, and calls the configured item handler.
- `IndexerActionItem`: abstract action payload. `PutDocumentActionItem` writes a document to a concrete `indexName`; `CompleteIndexActionItem` marks the action stream as complete.
- `IndexerDocumentStore`: target document-store abstraction. The default document store is in-memory.
- `DocumentStoreMetadataRepository`: id-first metadata abstraction for targets, indexers, publications, manifests, and mutation state. The default repository is in-memory.

### Document Store Publishing Model

Document-store publishing separates public target routing from physical index execution:

- `TargetDefinitionRecord` is the public/base target exposed to API callers. It is resolved by `targetUid` or `targetName`.
- `TargetRecord` is the concrete target used internally for one period bucket, or the single concrete target when the period strategy is `NONE`.
- `targetUid` is the public token for a target definition. A separate target-token model is not used yet.
- `targetName` on a target definition is the logical business target name, such as `customers`.
- concrete period target names are encoded as `{baseTargetName}--{periodKey}`, such as `customers--2026-05`.
- supported period strategies are `NONE`, `MONTHLY`, `HALF_YEARLY`, and `YEARLY`.
- period routing uses UTC.
- `indexName` is the concrete physical document-store index, such as `customers-2024-01J...`.
- the indexer id or uid identifies one durable physical index version.
- `PUBLISHED` means queryable; it does not mean immutable.
- publication state and mutation state are separate, so a valid physical index can be `PUBLISHED` and `WRITABLE`.

Repository access for document-store metadata is id-first. Identity lookup uses `id` or `uid`, and relationship lookup uses foreign ids such as `targetDefinitionId`, concrete `targetId`, and `indexerId`. Names remain stored for validation, display, and physical execution, but they are not the default repository access path.

Public write requests route by `targetUid` or `targetName` plus a timestamp when the target definition uses period routing. Command orchestration resolves the target definition, resolves the UTC period, ensures the concrete `TargetRecord`, resolves or provisions a writable indexer for that concrete target, expands logical mutations to concrete `IndexerActionItem` payloads, and publishes those concrete actions to each resolved indexer queue. Runtime action items execute by concrete `targetId`, `indexerId`, and `indexName`.

If a concrete target has no writable indexer during public-target submission, the command path attempts to provision the first writable indexer and moves the concrete target through `PROVISIONING` and back to `READY`. Provisioning failure marks the target `FAILED`, so later writes fail fast instead of repeatedly creating indexers.

Query routing resolves published indexers by `targetId` and queries only the resulting concrete `indexName` values. A target may have zero published indexes during first build, multiple writable indexes during rebuild, and multiple published indexes when the query model requires it.

Routing is expected to grow a hot metadata layer that keeps an in-memory view of operational targets and indexers for the action-item flow. That hot view should contain only records eligible for fast routing decisions and should decide whether an action can be forwarded directly to a hot target/indexer or must fall back to `SubmitIndexActionsCommand`. Broader metadata inspection belongs to an administration layer that can load targets and indexers with wider status/state filters. Repository query-object methods such as `listTargets(TargetMetadataQuery)` and `listIndexers(IndexerMetadataQuery)` support both layers without baking hot-routing filters into the repository itself.

### Index Flow

Indexing uses two paths:

- Hot path: if the target indexer is already known to be publish-ready, callers submit `IndexerActionItem` payloads through the indexer-facing producer API, which publishes to the configured `IndexerQueueClient`.
- Cold/unknown path: callers submit `SubmitIndexActionsCommand`. The command can carry concrete action destinations or a public `targetUid`/`targetName` plus timestamp. The command handler resolves writable metadata indexers by concrete `targetId`, verifies each destination, expands logical actions to concrete `indexerId`/`indexName` payloads, publishes a lifecycle wake-up, then publishes the concrete actions to scoped `IndexerQueuePublisher` endpoints.

Command completion means that the submitted actions were handed to the indexer queue, not that the documents were already indexed. Runtime processing remains asynchronous.

The cold path fails closed. If the command sees an unexpected indexer state or action mismatch, it must not publish actions. Expected/idempotent cases include resolving a writable, available, provisioned, runtime-active metadata indexer and waking runtime consumers that may not be hot yet.

The current fail-closed guards are:

- an unavailable or deleting indexer cannot receive new actions;
- an indexer whose provisioning state is not `READY` cannot receive new actions;
- a runtime-`NON_ACTIVE` indexer cannot receive new actions;
- logical actions must carry `targetId` so writable indexers can be resolved;
- concrete actions with `indexerId` and `indexName` must match the resolved metadata indexer.

### Lifecycle

- `activate()`: starts the root indexer once. It activates the queue consumer/listener, resumes consumption, and emits `INDEXER_STARTED`. Repeated calls are idempotent.
- `unregister()`: closes the active consumer/listener. It may inspect `nextIndexer`, but an active consumer on `nextIndexer` is unexpected because chained indexers are not roots by definition.
- `openProducer()` / `closeProducer()`: open or close the producer-side queue publisher for this indexer.
- `openConsumer()` / `closeConsumer()`: open or close the consumer-side processor for this indexer.
- `close()`: closes local producer and consumer handles. It does not delete queue resources or drop document-store indexes.
- `delete()`: retained as a local runtime close operation that returns the model. Destructive cleanup belongs to command orchestration and resource cleaners.

`nextIndexer` represents a chained/replacement indexer, not another root consumer. If `nextIndexer` is listening to a queue consumer, that should be treated as unexpected behavior and surfaced before the delete/unregister flow is finalized.

### Distributed Lifecycle Commands

Lifecycle commands express durable desired state. `ActivateIndexerCommand` and `DeactivateIndexerCommand` are handled through the generic `CommandService` layer. Their handlers update `DocumentStoreMetadataRepository` runtime state/version and publish an `IndexerMetadataChanged` notification with the indexer id, command type, and resulting version. `IndexerRuntimeState.ACTIVE/NON_ACTIVE` is a consumer-control switch only; it does not change publication state, mutation state, provisioning state, or indexer availability.

The metadata-change notification is a fan-out wake-up for runtime nodes, not the source of truth. `IndexerRuntime` subscribes to metadata changes, reloads the latest metadata indexer identified by the event, maps it to an `IndexerModel` for runtime transport, and reconciles local resources from that model. Runtime construction can use the Verticle-backed constructor so active indexers deploy an `IndexerProcessorVerticle` while `IndexerRuntime` only tracks `Indexer` instances and never exposes Vert.x deployment ids. Production implementations should back `IndexerLifecycleEventBus` with a durable pub/sub topic. The in-memory implementation retains events and replays them to late subscribers for local tests.

Runtime reconciliation opens a consumer only when the metadata indexer is `IndexerStatus.AVAILABLE`, `IndexerProvisioningState.READY`, and `IndexerRuntimeState.ACTIVE`. Otherwise it closes the local runtime indexer. If the metadata indexer is marked `MutationState.DELETING`, runtime also invokes the configured resource cleaner while the metadata record still contains the concrete queue/index names needed for cleanup.

Indexer-scoped queue reset is an orchestration workflow, not an `Indexer` runtime method. Reset is a troubleshooting mechanism whose initial semantics are that future writes move to a clean queue, not that every old in-flight item is synchronously proven dead. For Kafka, prefer advancing a queue generation in metadata and publishing through a new generated topic name over deleting and recreating the same topic name in place. Runtime nodes learn the new queue name through metadata-change fan-out and reconcile onto the new consumer. Old topics can be deleted asynchronously by resource cleanup, and missing old topics remain expected idempotent cleanup misses. Strict old-consumer fencing or distributed close acknowledgement can be added later if reset must provide stronger "old items cannot be processed" guarantees.

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

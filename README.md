# inqwise-indexer

Vert.x 5.x starter library inspired by `vertx-elastic`, with a modular layout:

- `inqwise-common`: shared data objects and indexing request models.
- `inqwise-indexer`: indexing service contracts and default in-memory implementation.
- `inqwise-indexer-load`: load/reload orchestration helpers and load-specific metadata around the core indexer primitives.
- `inqwise-client`: client-side wrappers for the indexer service.
- `inqwise`: top-level facade for index actions and load orchestration.

## Indexer Behavior

`inqwise-indexer` provides a controlled transport pipeline for gently moving structured actions into a targeted document store:

- `Indexer`: front object for one internal indexer. It owns producer-side submission into the indexer queue, validates concrete action identity, delegates action-specific document writes to `IndexerAction`, and delegates consumer lifecycle to an `IndexerProcessor` when one is provided.
- `IndexerRole`: declares the indexer responsibility. `LIVE_WRITER` handles ongoing action streams. `LOAD_WRITER` handles finite historical loads.
- `IndexResourceOwnership`: declares whether an indexer owns the physical document index resource. Cleanup deletes a document index only for an `OWNER`; `ATTACHED` writers may share the same `indexName` without owning deletion.
- `IndexerProcessor`: consumer-side runtime abstraction. `VerticleIndexerProcessor` deploys an `IndexerProcessorVerticle` and hides the Vert.x deployment id.
- `IndexerProcessorVerticle`: owns queue consumption for one indexer, including pause, process, commit, and resume. It receives only an `ActionItemProcessHandler`, not the whole `Indexer`.
- `IndexerQueueClient`: buffer client abstraction for publisher and consumer handles. Production implementations can use Kafka or another durable transport. `InMemoryIndexerQueue` is a simple local/test implementation.
- `IndexerQueueResourceManager`: admin-side abstraction for ensuring and deleting queue resources. Queue provisioning and deletion are not part of the shared runtime queue client surface.
- `IndexerQueueConsumer`: consumer side of the queue. It owns bulk/portion delivery policy, exposes `pause`, `resume`, `commit`, and `close`, and calls the configured item handler.
- `IndexerActionItem`: abstract action payload. `PutDocumentActionItem` and `RemoveDocumentActionItem` carry document mutations and may be expanded from logical requests to concrete target/indexer/index payloads. `CompleteIndexActionItem` is an internal marker for historical load completion; `CatchUpBarrierActionItem` is an internal marker proving a live writer consumed earlier queued catch-up actions.
- `IndexerActionProvider`: action-type extension point for processing and route normalization. Document action providers create concrete queue payloads through `IndexerActionItems`.
- `IndexerProvider`: indexer-type extension point for loading and composing indexer views. The default metadata-backed provider exposes hot routing capability only for eligible live writers.
- `IndexerDocumentStore`: target document-store abstraction. The default document store is in-memory.
- `DocumentStoreMetadataRepository`: id-first metadata abstraction for targets, indexers, publications, manifests, and mutation state. The default repository is in-memory.
- `CreateIndexerCommand`: generic command for inserting durable indexer metadata with role and index ownership. Load-specific orchestration composes this primitive rather than introducing a separate load-only create command.
- `ReplacePublishedIndexer`: metadata primitive for atomically retiring the old published indexer for a target and publishing a replacement indexer.

### Document Store Publishing Model

Document-store publishing separates public target routing from physical index execution:

- `TargetDefinition` is provider-owned application/static data, not repository metadata. It is resolved by `targetName`.
- `TargetRecord` is the concrete target used internally for one period bucket, or the single concrete target when the period strategy is `NONE`.
- `targetName` is the public route identity and logical business target name, such as `customers`.
- concrete period target names are encoded as `{baseTargetName}--{periodKey}`, such as `customers--2026-05`.
- supported period strategies are `NONE`, `MONTHLY`, `HALF_YEARLY`, and `YEARLY`.
- period routing uses UTC.
- `indexName` is the concrete physical document-store index, such as `customers-2024-01J...`.
- the indexer id or uid identifies one durable physical index version.
- `PUBLISHED` means queryable; it does not mean immutable.
- publication state and mutation state are separate, so a valid physical index can be `PUBLISHED` and `WRITABLE`.
- multiple indexers may share one physical `indexName` when their roles and ownership make that relationship explicit, for example a `LOAD_WRITER` owner plus an attached `LIVE_WRITER`.

Repository access for document-store metadata is id-first. Identity lookup uses concrete metadata `id` or `uid`, and relationship lookup uses foreign ids such as concrete `targetId` and `indexerId`. Target definitions are resolved through `TargetDefinitionProvider`, so the repository stores concrete targets by `targetName` plus `periodKey` rather than a target-definition foreign key.

Public write requests route by `targetName` plus a timestamp when the target definition uses period routing. Command orchestration resolves the target definition from `TargetDefinitionProvider`, resolves the UTC period, ensures the concrete `TargetRecord`, resolves or provisions a writable indexer for that concrete target, expands logical mutations to concrete `IndexerActionItem` payloads, and publishes those concrete actions to each resolved indexer queue. Runtime action items execute by concrete `targetId`, `indexerId`, and `indexName`.

If a concrete target has no writable indexer during public-target submission, the command path attempts to provision the first writable indexer and moves the concrete target through `PROVISIONING` and back to `READY`. Provisioning failure marks the target `FAILED`, so later writes fail fast instead of repeatedly creating indexers.

Query routing resolves the published indexer by `targetId` and queries only the resulting concrete `indexName`. A target may have zero published indexes during first build and multiple writable indexes during rebuild, but the first supported query contract allows at most one `PUBLISHED` indexer per target. More than one published indexer is an invariant failure.

Hot routing keeps an in-memory view of operational targets and indexers for the action-item flow. `HotMetadataView` loads a full immutable `HotTarget` snapshot from repository target records and `IndexerProvider` results, indexes it by target name, target uid, concrete target id, and indexer id, and invalidates the whole snapshot rather than patching child indexers in place. The default view uses only active, ready concrete targets and hot-capable live writers.

`HotTarget` owns target-level routing. It resolves the request timestamp to the configured UTC target period, selects the concrete target snapshot, and routes each action to every hot live writer under that concrete target. Hot routing is batch-atomic: either every action is accepted by at least one hot indexer and grouped into `RoutedIndexActions`, or the original request falls back unchanged to `SubmitIndexActionsCommand`.

`HotIndexer` owns final per-indexer acceptance and concrete action expansion. `MetadataHotIndexer` delegates action-specific normalization to `IndexerActionProvider.router()`. Candidate routing skips non-matching indexers; direct routing throws on conflicting concrete fields. This keeps action-specific rules out of the target cache and lets future providers add indexer-specific composition without changing the cache.

`HotIndexActionsService` is the hot-path entry point. It first tries a target-envelope route by `targetName` plus timestamp, then tries direct concrete routing for actions that carry an `indexerId`, and otherwise submits the original request to the command service. `RoutedIndexActionPublisher` is shared by hot routing and the cold command path for queue publication.

Hot routing support also includes two standalone guard components. `InvalidRouteCache` stores expiring invalid route signatures for stable cold failures such as missing target definitions, missing concrete targets/indexers, or missing writable indexers. `HotIndexActionsService` checks this cache before falling back to `SubmitIndexActionsCommand`; cached invalid routes fail fast instead of repeatedly entering cold submit. Retryable provisioning states and operator-recovery failures are not cached. `TargetInvalidationRegistry` stores versioned, expiring target invalidation entries for background cache invalidation when event delivery is missed or delayed. The target invalidation registry is not consulted on every hot route.

Broader metadata inspection belongs to an administration layer that can load targets and indexers with wider status/state filters. Repository query-object methods such as `listTargets(TargetMetadataQuery)` and `listIndexers(IndexerMetadataQuery)` and the generic `IndexerProvider` read APIs support both hot routing and administration without baking hot-routing filters into the repository itself.

### Index Flow

Indexing uses two paths:

- Hot path: callers submit a `HotIndexActionsRequest` to `HotIndexActionsService`. A normal live-write request carries `targetName`, a timestamp, and logical document mutation items. If the cached target/indexer snapshot proves the route, the service expands the items to concrete `targetId`, `indexerId`, and `indexName` payloads and publishes them through `RoutedIndexActionPublisher`.
- Cold/unknown path: callers submit `SubmitIndexActionsCommand`, either directly or through hot-path fallback. The command supports only two routing schemas. Target-envelope mode carries `targetName`, optional timestamp, and logical document mutation actions with no concrete destination fields. Concrete mode carries no target envelope or timestamp, and every action must include a concrete `targetId` or `indexerId`; internal actions such as complete and catch-up barrier require `indexerId`. The command handler resolves writable metadata indexers by concrete `targetId`, verifies each destination, expands logical actions to concrete `indexerId`/`indexName` payloads, publishes metadata-change wake-ups only for real metadata changes such as auto-provisioned indexers, then publishes the concrete actions through the shared `RoutedIndexActionPublisher`.

Command completion means that the submitted actions were handed to the indexer queue, not that the documents were already indexed. Runtime processing remains asynchronous.

The cold path fails closed. If the command sees an unexpected indexer state or action mismatch, it must not publish actions. Expected/idempotent cases include resolving a writable, available, provisioned, runtime-active metadata indexer and waking runtime consumers that may not be hot yet.

Cold command failures use typed `CommandFailure` classification where the command layer needs retry decisions. Concurrent provisioning states, including target provisioning already in progress and target provisioning lock/version conflicts, are `RETRYABLE`. Stable invalid routes, including missing target definitions, missing concrete targets/indexers, and missing writable indexers, are `STABLE_INVALID` and may be written to `InvalidRouteCache`. Target provisioning marked `FAILED` is final for the submit command and requires explicit recovery before the same route should be retried.

The current fail-closed guards are:

- an unavailable or deleting indexer cannot receive new actions;
- an indexer whose provisioning state is not `READY` cannot receive new actions;
- a runtime-`NON_ACTIVE` indexer cannot receive new actions;
- cold concrete-target routing requires either target-envelope document mutations or concrete route fields so writable indexers can be resolved;
- concrete actions with `indexerId` and `indexName` must match the resolved metadata indexer.

Routing identity fields are immutable by definition for normal lifecycle: `targetId`, `targetName`, `indexName`, `queueName`, `role`, and `type`. If these values are wrong, create a replacement indexer instead of mutating the existing record. `ResetIndexerQueueCommand` remains a narrow troubleshooting exception in the current codebase and must not be used as a normal reload/live-writer swap mechanism.

### Load And Reload Workflow

Durable load/reload orchestration is split between the core `indexer` module and the `indexer-load` module. Core owns indexer roles, resource ownership, runtime marker processing hooks, and generic indexer commands. `indexer-load` owns load workflow metadata, external loader contracts, and marker handlers.

- `LOAD_WRITER`: finite historical load writer for a target and physical `indexName`.
- `LIVE_WRITER`: ongoing writer. During reload, a new live writer may be linked to the load workflow and write to the same `indexName` as the load writer.
- `IndexerLoadRecord`: load-plugin metadata keyed by the core `indexerId` of the load writer. It tracks target id, load state, optional linked live writer id, timestamp replay window, review requirement and approval, barrier progress, and failure details. The load repository enforces one active load per target.
- `LiveWriterPolicy`: live writer creation is explicit. A load may create no live writer, create one immediately, or create one lazily on the first live action when the policy allows it.

`CompleteIndexActionItem` and `CatchUpBarrierActionItem` are internal actions. External loaders should use loader-facing helpers such as `LoadWriter.submit(...)` and `LoadWriter.complete(...)` rather than constructing marker items directly. `QueueLoadWriter.complete(...)` publishes the internal completion marker to the load writer queue.

`CreateLoadCommand` is the first load orchestration command. It ensures the target, creates the core `LOAD_WRITER`, optionally creates an immediate linked `LIVE_WRITER`, stores the provider id and load source fields (`sourceFrom`, `sourceTo`, `sourceQuery`, `sourcePlaybookId`) in `IndexerLoadRecord`, publishes metadata-change wake-ups, and starts the application-level `LoadProvider` resolved from `LoadProviderRegistry`. The `LoadRequest` includes both source fields and concrete callback identities such as `targetId`, `indexerId`, optional `liveIndexerId`, `providerId`, `indexName`, and `queueName`.

Historical-only loads publish the `LOAD_WRITER` after successful completion unless review is required; publication converts that indexer role to `LIVE_WRITER` while preserving physical index ownership. Loads with live support publish the linked `LIVE_WRITER` after historical completion, catch-up barrier processing, and optional review. `PublishLoadCommand` validates the load state, candidate writer state, optional approval, and catch-up barrier before calling the atomic metadata replace primitive. When a linked live writer is published, ownership moves from the load writer to the linked live writer and the old published writer is retired.

`ApproveLoadPublicationCommand` records `approvedAt`, `approvedBy`, and `approvalReason`. If approval makes the load publishable, it submits `PublishLoadCommand` through the command service. Marker handling can also auto-publish non-reviewed loads when a command service is supplied: historical-only loads publish after the completion marker, and linked live loads publish after the catch-up barrier marker.

`CancelLoadCommand` resolves the stored provider id from `IndexerLoadRecord`, stops that provider, marks the load `CANCELLED`, and, when wired with a command service, submits generic delete commands for the load writer and optional linked live writer. Published loads are not cancellable through this command.

`LoadAwareIndexerEventPublisher` is the load-side runtime failure bridge. It observes `ACTION_ITEM_FAILED` events, finds an active load for the failing indexer id, marks the load `FAILED`, attempts to stop the stored provider, and then delegates the original event. Runtime processing stays generic; load workflow state remains in `indexer-load`.

When `PublishLoadCommandHandler` is wired with a command service, successful publication submits `CleanupPublishedLoadCommand`. Cleanup reloads current metadata versions and uses the generic `DeleteIndexerCommand` path for the old published writer and, when a linked live writer was published, the load writer. This marks those indexers `DELETING` and `NON_ACTIVE`; runtime resource cleanup still follows `IndexResourceOwnership`, not name scanning.

Timestamp-based live catch-up uses the configured replay window to decide which live actions are copied to the candidate writer. Duplicate/retry safety is assumed only inside the same partition/key ordering scope.

### Lifecycle

- `activate()`: starts the root indexer once. It activates the queue consumer/listener, resumes consumption, and emits `INDEXER_STARTED`. Repeated calls are idempotent.
- `unregister()`: closes the active consumer/listener.
- `openProducer()` / `closeProducer()`: open or close the producer-side queue publisher for this indexer.
- `openConsumer()` / `closeConsumer()`: open or close the consumer-side processor for this indexer.
- `close()`: closes local producer and consumer handles. It does not delete queue resources or drop document-store indexes.
- `delete()`: retained as a local runtime close operation that returns the model. Destructive cleanup belongs to command orchestration and resource cleaners.

### Distributed Lifecycle Commands

Lifecycle commands express durable desired state. `ActivateIndexerCommand` and `DeactivateIndexerCommand` are handled through the generic `CommandService` layer. Their handlers update `DocumentStoreMetadataRepository` runtime state/version and publish an `IndexerMetadataChanged` notification with the indexer id, command type, and resulting version. `IndexerRuntimeState.ACTIVE/NON_ACTIVE` is a consumer-control switch only; it does not change publication state, mutation state, provisioning state, or indexer availability.

The metadata-change notification is a fan-out wake-up for runtime nodes, not the source of truth. `IndexerRuntime` subscribes to metadata changes, reloads the latest metadata indexer identified by the event, maps it to an `IndexerModel` for runtime transport, and reconciles local resources from that model. Runtime construction can use the Verticle-backed constructor so active indexers deploy an `IndexerProcessorVerticle` while `IndexerRuntime` only tracks `Indexer` instances and never exposes Vert.x deployment ids. Production implementations should back `IndexerLifecycleEventBus` with a durable pub/sub topic. The in-memory implementation retains events and replays them to late subscribers for local tests.

Runtime reconciliation opens a consumer only when the metadata indexer is `IndexerStatus.AVAILABLE`, `IndexerProvisioningState.READY`, and `IndexerRuntimeState.ACTIVE`. Otherwise it closes the local runtime indexer. If the metadata indexer is marked `MutationState.DELETING`, runtime also invokes the configured resource cleaner while the metadata record still contains the concrete queue/index names needed for cleanup.

Indexer-scoped queue reset is an orchestration workflow, not an `Indexer` runtime method. Reset is a troubleshooting mechanism whose initial semantics are that future writes move to a clean queue, not that every old in-flight item is synchronously proven dead. For Kafka, prefer advancing a queue generation in metadata and publishing through a new generated topic name over deleting and recreating the same topic name in place. Runtime nodes learn the new queue name through metadata-change fan-out and reconcile onto the new consumer. Old topics can be deleted asynchronously by resource cleanup, and missing old topics remain expected idempotent cleanup misses. Strict old-consumer fencing or distributed close acknowledgement can be added later if reset must provide stronger "old items cannot be processed" guarantees.

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

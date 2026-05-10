# inqwise-indexer

Vert.x 5.x starter library inspired by `vertx-elastic`, with a modular layout:

- `inqwise-common`: shared data objects and indexing request models.
- `inqwise-indexer`: indexing service contracts and default in-memory implementation.
- `inqwise-client`: client-side wrappers for the indexer service.
- `inqwise`: top-level facade for index actions and preload orchestration.

## Indexer Behavior

`inqwise-indexer` provides the core indexing concepts from `vertx-elastic` using Vert.x 5 APIs:

- `Indexer`: active indexer that applies `PUT` and `REMOVE` actions to an `IndexerDocumentStore`.
- `PreloadIndexer`: temporary indexer that receives preload batches on its Vert.x event bus address, writes them to a chained replacement `Indexer`, and promotes that replacement when the final preload batch arrives.
- `DefaultIndexerService`: service facade for creating indexers, routing index actions by target name, deleting indexers, and reporting status.

The default document store is in-memory. Production callers can pass their own `IndexerDocumentStore` to `DefaultIndexerService`.

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

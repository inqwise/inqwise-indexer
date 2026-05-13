package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import com.inqwise.indexer.commands.ActivateIndexerCommand;
import com.inqwise.indexer.commands.ActivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeactivateIndexerCommand;
import com.inqwise.indexer.commands.DeactivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeleteIndexerCommand;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerRuntimeTest {
	@Test
	void activateCommandStartsLocalRuntimeIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		AtomicInteger started = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			model -> new Indexer(
				vertx,
				model,
				new InMemoryIndexerQueue(),
				repository,
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				event -> {
					if (event.getType() == IndexerEventType.INDEXER_STARTED) {
						started.incrementAndGet();
					}

					return Future.succeededFuture();
				}
			)
		);

		repository.save(inactiveModel())
			.compose(id -> runtime.start()
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id)))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerStatus.STARTED, found.orElseThrow().getStatus());
				assertEquals(1L, found.get().getVersion());
				assertEquals(1, started.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deactivateCommandDoesNotUnregisterByDefault(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		AtomicInteger stopped = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			model -> new Indexer(
				vertx,
				model,
				new InMemoryIndexerQueue(),
				repository,
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				event -> {
					if (event.getType() == IndexerEventType.INDEXER_STOPPED) {
						stopped.incrementAndGet();
					}

					return Future.succeededFuture();
				}
			)
		);

		repository.save(activeModel())
			.compose(id -> runtime.start()
				.compose(ignored -> runtime.reconcile(id))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id)))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerStatus.NON_ACTIVE, found.orElseThrow().getStatus());
				assertEquals(1L, found.get().getVersion());
				assertEquals(0, stopped.get());
				testContext.completeNow();
			})));
	}

	@Test
	void missingRepositoryRecordClosesLocalRuntimeIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger unregistered = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			model -> new TestIndexer(
				vertx,
				model,
				activated,
				unregistered,
				closed
			)
		);

		repository.save(activeModel())
			.compose(id -> runtime.reconcile(id)
				.compose(ignored -> repository.delete(id))
				.compose(ignored -> runtime.reconcile(id)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, activated.get());
				assertEquals(0, unregistered.get());
				assertEquals(1, closed.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deleteCommandClosesLocalIndexerBeforeCleaningResources(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger unregistered = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		AtomicInteger cleaned = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			model -> new TestIndexer(
				vertx,
				model,
				activated,
				unregistered,
				closed
			),
			model -> {
				assertEquals(1, closed.get());
				assertEquals("customers_1", model.getIndexName());
				cleaned.incrementAndGet();
				return Future.succeededFuture();
			}
		);

		repository.save(activeModel())
			.compose(id -> runtime.start()
				.compose(ignored -> runtime.reconcile(id))
				.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id)))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerStatus.DELETED, found.orElseThrow().getStatus());
				assertEquals(1, activated.get());
				assertEquals(0, unregistered.get());
				assertEquals(1, closed.get());
				assertEquals(1, cleaned.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deletedReconcileWithoutLocalIndexerStillCleansResources(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger cleaned = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			model -> {
				throw new AssertionError("Deleted reconcile must not activate");
			},
			model -> {
				assertEquals("customers_1", model.getIndexName());
				cleaned.incrementAndGet();
				return Future.succeededFuture();
			}
		);

		repository.save(deletedModel())
			.compose(runtime::reconcile)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, cleaned.get());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanerFailureFailsDeletedReconcileForRetry(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger cleaned = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			model -> {
				throw new AssertionError("Deleted reconcile must not activate");
			},
			model -> {
				cleaned.incrementAndGet();
				return Future.failedFuture("cleanup failed");
			}
		);

		repository.save(deletedModel())
			.compose(runtime::reconcile)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("cleanup failed"));
				assertEquals(1, cleaned.get());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryIndexerRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandService()
			.register(new ActivateIndexerCommandHandler(repository, eventBus))
			.register(new DeactivateIndexerCommandHandler(repository, eventBus))
			.register(new DeleteIndexerCommandHandler(repository, eventBus));
	}

	private IndexerModel inactiveModel() {
		return IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withStatus(IndexerStatus.NON_ACTIVE)
			.build();
	}

	private IndexerModel activeModel() {
		return IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withStatus(IndexerStatus.STARTED)
			.build();
	}

	private IndexerModel deletedModel() {
		return IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withStatus(IndexerStatus.DELETED)
			.build();
	}

	private static class TestIndexer extends Indexer {
		private final AtomicInteger activated;
		private final AtomicInteger unregistered;
		private final AtomicInteger closed;

		private TestIndexer(
			Vertx vertx,
			IndexerModel model,
			AtomicInteger activated,
			AtomicInteger unregistered,
			AtomicInteger closed
		) {
			super(vertx, model, new InMemoryIndexerDocumentStore());
			this.activated = activated;
			this.unregistered = unregistered;
			this.closed = closed;
		}

		@Override
		public Future<Void> activate() {
			activated.incrementAndGet();
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> unregister() {
			unregistered.incrementAndGet();
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> close() {
			closed.incrementAndGet();
			return Future.succeededFuture();
		}
	}
}

package com.inqwise.indexer.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.runtime.Indexer;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.runtime.IndexerRuntime;
import com.inqwise.indexer.runtime.IndexerRuntimeReconciler;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.core.Vertx;

@ExtendWith(VertxExtension.class)
class RuntimeServiceVerticleTest {
	@Test
	void reconcilesAndReportsLocalRuntimeStatus(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerRuntime runtime = new IndexerRuntime(
			indexer -> new Indexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				documentStore
			)
		);
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);

		repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				"runtime",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> reconciler.start().compose(ignored -> vertx.deployVerticle(
				new RuntimeServiceVerticle(
				runtime,
				reconciler
			)))
				.compose(ignored -> RuntimeServices.proxy(vertx)
					.reconcileIndexer(new RuntimeReconcileRequest().setIndexerId(indexerId)))
				.compose(ignored -> RuntimeServices.proxy(vertx).status()))
			.onComplete(testContext.succeeding(status -> testContext.verify(() -> {
				assertEquals(1, status.getIndexers().size());
				RuntimeIndexerStatus indexer = status.getIndexers().get(0);
				assertEquals("customers", indexer.getTargetName());
				assertEquals("customers-index", indexer.getIndexName());
				assertEquals("customers-queue", indexer.getQueueName());
				testContext.completeNow();
			})));
	}
}

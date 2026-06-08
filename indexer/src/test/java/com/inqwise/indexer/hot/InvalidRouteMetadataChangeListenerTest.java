package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InvalidRouteMetadataChangeListenerTest {
	@Test
	void invalidatesTargetEnvelopeRoutesWhenIndexerMetadataChanges(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InvalidRouteSignature targetRoute = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			com.inqwise.indexer.IndexerActionType.PUT_DOCUMENT
		);
		cache.record(targetRoute, "missing target");

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> new InvalidRouteMetadataChangeListener(
				repository,
				eventBus,
				cache
			).start().compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
				indexerId,
				"indexer.changed",
				0L
			))))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(cache.find(targetRoute).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void invalidatesDirectRoutesWhenIndexerMetadataChanges(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> {
				InvalidRouteSignature targetIdRoute = new InvalidRouteSignature(
					null,
					null,
					targetId,
					null,
					null,
					com.inqwise.indexer.IndexerActionType.PUT_DOCUMENT
				);
				InvalidRouteSignature indexerIdRoute = new InvalidRouteSignature(
					null,
					null,
					null,
					indexerId,
					null,
					com.inqwise.indexer.IndexerActionType.PUT_DOCUMENT
				);
				cache.record(targetIdRoute, "missing target");
				cache.record(indexerIdRoute, "missing indexer");

				return new InvalidRouteMetadataChangeListener(
					repository,
					eventBus,
					cache
				).start().compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
					indexerId,
					"indexer.changed",
					0L
				))).map(ignored -> new InvalidRouteSignature[] {
					targetIdRoute,
					indexerIdRoute
				});
			}))
			.onComplete(testContext.succeeding(routes -> testContext.verify(() -> {
				assertTrue(cache.find(routes[0]).isEmpty());
				assertTrue(cache.find(routes[1]).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void ignoresMissingIndexerMetadataChange(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InvalidRouteSignature route = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			com.inqwise.indexer.IndexerActionType.PUT_DOCUMENT
		);
		cache.record(route, "missing target");

		new InvalidRouteMetadataChangeListener(repository, eventBus, cache)
			.start()
			.compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
				404,
				"indexer.changed",
				0L
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(cache.find(route).isPresent());
				testContext.completeNow();
			})));
	}
}

package com.inqwise.indexer.hot;

import com.inqwise.indexer.adapters.local.InMemoryInvalidRouteCache;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.routing.InvalidRouteSignature;

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
		InvalidRouteSignature broadRoute = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature mayRoute = new InvalidRouteSignature(
			"customers",
			"2026-05",
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature juneRoute = new InvalidRouteSignature(
			"customers",
			"2026-06",
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		cache.record(broadRoute, "missing target");
		cache.record(mayRoute, "missing target");
		cache.record(juneRoute, "missing target");

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			"2026-05",
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.READY
		))
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
			)).compose(indexerId -> new InvalidRouteMetadataChangeListener(
				repository,
				eventBus,
				cache
			).start().compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
				indexerId,
				targetId,
				"indexer.changed",
				0L
			)))))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(cache.find(broadRoute).isEmpty());
				assertTrue(cache.find(mayRoute).isEmpty());
				assertTrue(cache.find(juneRoute).isPresent());
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
					com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
				);
				InvalidRouteSignature indexerIdRoute = new InvalidRouteSignature(
					null,
					null,
					null,
					indexerId,
					null,
					com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
				);
				cache.record(targetIdRoute, "missing target");
				cache.record(indexerIdRoute, "missing indexer");

				return new InvalidRouteMetadataChangeListener(
					repository,
					eventBus,
					cache
				).start().compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
					indexerId,
					targetId,
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
	void invalidatesDirectRoutesWhenIndexerMetadataIsMissing(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InvalidRouteSignature envelopeRoute = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature targetIdRoute = new InvalidRouteSignature(
			null,
			null,
			1,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature indexerIdRoute = new InvalidRouteSignature(
			null,
			null,
			null,
			404,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		cache.record(envelopeRoute, "missing target");
		cache.record(targetIdRoute, "missing target");
		cache.record(indexerIdRoute, "missing indexer");

		new InvalidRouteMetadataChangeListener(repository, eventBus, cache)
			.start()
			.compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
				404,
				1,
				"indexer.changed",
				0L
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(cache.find(envelopeRoute).isPresent());
				assertTrue(cache.find(targetIdRoute).isEmpty());
				assertTrue(cache.find(indexerIdRoute).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void invalidatesTargetRoutesWhenTargetMetadataChanges(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			"2026-05",
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.READY
		))
			.compose(targetId -> {
				InvalidRouteSignature broadTargetNameRoute = new InvalidRouteSignature(
					"customers",
					null,
					null,
					null,
					null,
					com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
				);
				InvalidRouteSignature mayTargetNameRoute = new InvalidRouteSignature(
					"customers",
					"2026-05",
					null,
					null,
					null,
					com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
				);
				InvalidRouteSignature juneTargetNameRoute = new InvalidRouteSignature(
					"customers",
					"2026-06",
					null,
					null,
					null,
					com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
				);
				InvalidRouteSignature targetIdRoute = new InvalidRouteSignature(
					null,
					null,
					targetId,
					null,
					null,
					com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
				);
				cache.record(broadTargetNameRoute, "missing target");
				cache.record(mayTargetNameRoute, "missing target");
				cache.record(juneTargetNameRoute, "missing target");
				cache.record(targetIdRoute, "missing target");

				return new InvalidRouteMetadataChangeListener(
					repository,
					eventBus,
					cache
				).start().compose(ignored -> eventBus.publish(new TargetMetadataChanged(
					targetId,
					"customers",
					"2026-05",
					"target.changed",
					0L
				))).map(ignored -> new InvalidRouteSignature[] {
					broadTargetNameRoute,
					mayTargetNameRoute,
					juneTargetNameRoute,
					targetIdRoute
				});
			})
			.onComplete(testContext.succeeding((InvalidRouteSignature[] routes) -> testContext.verify(() -> {
				assertTrue(cache.find(routes[0]).isEmpty());
				assertTrue(cache.find(routes[1]).isEmpty());
				assertTrue(cache.find(routes[2]).isPresent());
				assertTrue(cache.find(routes[3]).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void invalidatesTargetRoutesWhenTargetMetadataIsMissing(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InvalidRouteSignature broadTargetNameRoute = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature mayTargetNameRoute = new InvalidRouteSignature(
			"customers",
			"2026-05",
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature juneTargetNameRoute = new InvalidRouteSignature(
			"customers",
			"2026-06",
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		InvalidRouteSignature targetIdRoute = new InvalidRouteSignature(
			null,
			null,
			50,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		cache.record(broadTargetNameRoute, "missing target");
		cache.record(mayTargetNameRoute, "missing target");
		cache.record(juneTargetNameRoute, "missing target");
		cache.record(targetIdRoute, "missing target");

		new InvalidRouteMetadataChangeListener(repository, eventBus, cache)
			.start()
			.compose(ignored -> eventBus.publish(new TargetMetadataChanged(
				50,
				"customers",
				"2026-05",
				"target.changed",
				0L
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(cache.find(broadTargetNameRoute).isEmpty());
				assertTrue(cache.find(mayTargetNameRoute).isEmpty());
				assertTrue(cache.find(juneTargetNameRoute).isPresent());
				assertTrue(cache.find(targetIdRoute).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void startIsIdempotent(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryInvalidRouteCache cache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InvalidRouteMetadataChangeListener listener =
			new InvalidRouteMetadataChangeListener(repository, eventBus, cache);
		InvalidRouteSignature route = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			com.inqwise.indexer.actions.IndexerActionType.PUT_DOCUMENT
		);
		cache.record(route, "missing target");

		listener.start()
			.compose(ignored -> listener.start())
			.compose(ignored -> eventBus.publish(new TargetMetadataChanged(
				10,
				"customers",
				null,
				"target.changed",
				1L
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(cache.find(route).isEmpty());
				testContext.completeNow();
			})));
	}
}

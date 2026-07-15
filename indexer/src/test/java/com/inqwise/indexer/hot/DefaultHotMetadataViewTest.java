package com.inqwise.indexer.hot;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.ConcreteTargetKey;
import com.inqwise.indexer.metadata.DeleteTarget;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.targets.TargetPeriod;
import com.inqwise.indexer.catalog.targets.TargetPeriodResolver;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.MetadataIndexerProvider;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultHotMetadataViewTest {
	@Test
	void refreshLoadsTargetSnapshotAndLookupMaps(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		DefaultHotMetadataView view = view(repository);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id())
				.map(target))
			.onComplete(testContext.succeeding(target -> testContext.verify(() -> {
				assertTrue(view.findTargetByName("customers").isPresent());
				assertTrue(view.findIndexerById(1).isPresent());

				HotRouteResult result = view.findTargetByName("customers").orElseThrow()
					.route(new HotIndexActionsRequest(
						"customers",
						Instant.parse("2026-05-18T10:15:00Z"),
						List.of(IndexerActionItems.putDocument(
							"42",
							new JsonObject().put("name", "Ada")
						))
					));
				HotRouteResult.Routed routed = (HotRouteResult.Routed) result;
				assertEquals(1, routed.groups().size());
				assertEquals("queue-customers", routed.groups().get(0).queueName());
				testContext.completeNow();
			})));
	}

	@Test
	void invalidateByConcreteTargetRemovesFullTargetSnapshot(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		DefaultHotMetadataView view = view(repository);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id()).map(target))
			.onComplete(testContext.succeeding(target -> testContext.verify(() -> {
				view.invalidateHotTargetByConcreteTargetId(target.id());

				assertTrue(view.findTargetByName("customers").isEmpty());
				assertTrue(view.findIndexerById(1).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void invalidateByIndexerRemovesOwningTargetSnapshot(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		DefaultHotMetadataView view = view(repository);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id()).map(target))
			.onComplete(testContext.succeeding(target -> testContext.verify(() -> {
				view.invalidateHotTargetByIndexerId(1);

				assertTrue(view.findTargetByName("customers").isEmpty());
				assertTrue(view.findIndexerById(1).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void invalidateAllRemovesEveryLookup(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		DefaultHotMetadataView view = view(repository);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id()))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				view.invalidateAllHotTargets();

				assertTrue(view.findTargetByName("customers").isEmpty());
				assertTrue(view.findIndexerById(1).isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void refreshMissingTargetInvalidatesExistingSnapshot(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		DefaultHotMetadataView view = view(repository);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id())
				.compose(ignored -> repository.deleteTarget(new DeleteTarget(
					target.id(),
					target.version()
				)))
				.compose(ignored -> view.refreshHotTargetByConcreteTargetId(target.id())))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(view.findTargetByName("customers").isEmpty());
				assertTrue(view.findIndexerById(1).isEmpty());
				testContext.completeNow();
			})));
	}

	private DefaultHotMetadataView view(InMemoryDocumentStoreMetadataRepository repository) {
		return new DefaultHotMetadataView(
			repository,
			targetDefinitionProvider(),
			new IndexerProviders(List.of(new MetadataIndexerProvider(repository)))
		);
	}

	private Future<TargetRecord> insertReadyMonthlyTargetWithIndexer(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		TargetPeriodResolver resolver = new TargetPeriodResolver();
		TargetPeriod period = resolver.resolve(
			TargetPeriodStrategy.MONTHLY,
			Instant.parse("2026-05-18T10:15:00Z")
		);
		return repository.ensureTarget("customers", period)
			.compose(target -> repository.insertIndexer(new InsertIndexer(
				"indexer-customers",
				target.id(),
				target.targetName(),
				"customers-index",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> repository.getTargetByDefinitionAndPeriod(
				new ConcreteTargetKey(target.targetName(), target.periodKey())
			)).map(found -> found.orElseThrow()));
	}

	private StaticTargetDefinitionProvider targetDefinitionProvider() {
		return new StaticTargetDefinitionProvider(List.of(
			new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
		));
	}
}

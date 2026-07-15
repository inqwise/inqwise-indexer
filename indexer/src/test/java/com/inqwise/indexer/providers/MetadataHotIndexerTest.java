package com.inqwise.indexer.providers;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.providers.IndexerProviderQuery;
import com.inqwise.indexer.providers.IndexerProviders;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetadataHotIndexerTest {
	@Test
	void metadataProviderExposesHotIndexerForEligibleLiveWriter(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = providers(repository);

		insertIndexer(
			repository,
			IndexerRole.LIVE_WRITER,
			IndexerRuntimeState.ACTIVE,
			MutationState.WRITABLE
		).compose(id -> providers.getIndexerById(id))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertTrue(found.get().hotIndexer().isPresent());
				assertEquals(found.get().model().getId(), found.get().hotIndexer().get().id());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataProviderSkipsHotIndexerForLoadWriter(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = providers(repository);

		insertIndexer(
			repository,
			IndexerRole.LOAD_WRITER,
			IndexerRuntimeState.ACTIVE,
			MutationState.WRITABLE
		).compose(id -> providers.getIndexerById(id))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertTrue(found.get().hotIndexer().isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataHotIndexerRoutesLogicalPutToConcretePut(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = providers(repository);

		insertIndexer(
			repository,
			IndexerRole.LIVE_WRITER,
			IndexerRuntimeState.ACTIVE,
			MutationState.WRITABLE
		).compose(id -> providers.getIndexerById(id))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				HotIndexerCapability hotIndexer = found.orElseThrow().hotIndexer().orElseThrow();
				PutDocumentActionItem routed = (PutDocumentActionItem) hotIndexer.route(
					IndexerActionItems.putDocument("42", new JsonObject().put("name", "Ada")),
					IndexerActionRouteMode.CANDIDATE
				).orElseThrow();

				assertEquals(1, routed.getTargetId());
				assertEquals(found.get().model().getId(), routed.getIndexerId());
				assertEquals("customers-a", routed.getIndexName());
				assertEquals("Ada", routed.getDocument().getString("name"));
				testContext.completeNow();
			})));
	}

	@Test
	void metadataHotIndexerSkipsCandidateMismatchAndThrowsDirectMismatch(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = providers(repository);

		insertIndexer(
			repository,
			IndexerRole.LIVE_WRITER,
			IndexerRuntimeState.ACTIVE,
			MutationState.WRITABLE
		).compose(id -> providers.getIndexerById(id))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				HotIndexerCapability hotIndexer = found.orElseThrow().hotIndexer().orElseThrow();
				PutDocumentActionItem mismatched = IndexerActionItems.concretePutDocument(
					1,
					999,
					"customers-a",
					"42",
					new JsonObject()
				);

				assertTrue(hotIndexer.route(mismatched, IndexerActionRouteMode.CANDIDATE).isEmpty());
				assertThrows(
					IllegalArgumentException.class,
					() -> hotIndexer.route(mismatched, IndexerActionRouteMode.DIRECT)
				);
				testContext.completeNow();
			})));
	}

	@Test
	void providerListQueryCanSelectOnlyHotEligibleResolvedIndexers(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = providers(repository);

		insertIndexer(
			repository,
			IndexerRole.LIVE_WRITER,
			IndexerRuntimeState.ACTIVE,
			MutationState.WRITABLE
		).compose(firstId -> repository.insertIndexer(new InsertIndexer(
			"non-active-live",
			1,
			"customers",
			"customers-b",
			"queue-customers-b",
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.NON_ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		))).compose(secondId -> providers.listIndexers(new IndexerProviderQuery(
			null,
			null,
			List.of(IndexerType.INDEX),
			List.of(IndexerRole.LIVE_WRITER),
			null,
			null,
			List.of(IndexerRuntimeState.ACTIVE),
			null,
			List.of(MutationState.WRITABLE)
		))).onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
			assertEquals(1, indexers.size());
			assertTrue(indexers.get(0).hotIndexer().isPresent());
			testContext.completeNow();
		})));
	}

	private IndexerProviders providers(InMemoryDocumentStoreMetadataRepository repository) {
		return new IndexerProviders(List.of(new MetadataIndexerProvider(repository)));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRole role,
		IndexerRuntimeState runtimeState,
		MutationState mutationState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers-a",
				"queue-customers",
				IndexerType.INDEX,
				role,
				IndexResourceOwnership.OWNER,
				runtimeState,
				PublicationState.UNPUBLISHED,
				mutationState
			)));
	}
}

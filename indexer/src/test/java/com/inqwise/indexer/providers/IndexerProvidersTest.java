package com.inqwise.indexer.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerProvidersTest {
	@Test
	void metadataProviderListsIndexersByQuery(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = new IndexerProviders(List.of(
			new MetadataIndexerProvider(repository)
		));

		repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				"load-writer",
				targetId,
				"customers",
				"customers-a",
				"queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(loadId -> repository.insertIndexer(new InsertIndexer(
				"live-writer",
				targetId,
				"customers",
				"customers-a",
				"queue-live",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.ATTACHED,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(liveId -> providers.listIndexers(new IndexerProviderQuery(
				null,
				List.of(targetId),
				List.of(IndexerType.INDEX),
				List.of(IndexerRole.LIVE_WRITER),
				null,
				null,
				null,
				null,
				null
			)))))
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals(IndexerRole.LIVE_WRITER, indexers.get(0).model().getRole());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataProviderFindsIndexerById(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProviders providers = new IndexerProviders(List.of(
			new MetadataIndexerProvider(repository)
		));

		repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers-a",
				"queue-live",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(providers::getIndexerById)
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("customers-a", found.get().model().getIndexName());
				testContext.completeNow();
			})));
	}
}

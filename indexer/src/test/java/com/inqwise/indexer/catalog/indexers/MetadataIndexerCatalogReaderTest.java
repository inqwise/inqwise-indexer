package com.inqwise.indexer.catalog.indexers;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetadataIndexerCatalogReaderTest {
	@Test
	void mapsMetadataRecordsWithoutPublicationState(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerCatalogReader reader = new MetadataIndexerCatalogReader(repository);

		repository.insertTarget(readyTarget("target", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"indexer",
				targetId,
				"customers",
				"customers-2026-01",
				"customers-2026-01-actions",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> reader.findById(indexerId).compose(found -> {
				assertTrue(found.isPresent());
				assertEquals("indexer-1", found.orElseThrow().uid());
				return reader.findByUid("indexer-1");
			}))
			.compose(found -> {
				assertTrue(found.isPresent());
				return reader.list(new IndexerCatalogQuery(
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null
				));
			})
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertEquals(1, entries.size());
				assertEquals("customers-2026-01", entries.get(0).indexName());
				testContext.completeNow();
			})));
	}
}

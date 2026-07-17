package com.inqwise.indexer.catalog.targets;

import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetadataTargetCatalogReaderTest {
	@Test
	void mapsMetadataRecordsToCatalogEntries(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetCatalogReader reader = new MetadataTargetCatalogReader(repository);

		repository.insertTarget(readyTarget("target", "customers"))
			.compose(id -> reader.findById(id).compose(found -> {
				assertTrue(found.isPresent());
				assertEquals("target-1", found.orElseThrow().uid());
				return reader.findByUid("target-1");
			}))
			.compose(found -> {
				assertTrue(found.isPresent());
				return reader.list(new TargetCatalogQuery(null, null, null, null));
			})
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertEquals(1, entries.size());
				assertEquals("customers", entries.get(0).targetName());
				testContext.completeNow();
			})));
	}
}

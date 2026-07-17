package com.inqwise.indexer.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.targets.InitialPublicationMode;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class AdminCreateRequestResolverTest {
	@Test
	void createsTargetRequestWithGeneratedPrefixAndIndexerNames() {
		AdminCreateRequestResolver resolver =
			new AdminCreateRequestResolver(new InMemoryDocumentStoreMetadataRepository());

		AdminCreateTargetRequest request = resolver.target(
			"customers",
			Instant.parse("2026-06-07T00:00:00Z"),
			InitialPublicationMode.READY
		);

		assertTrue(request.getPrefix().matches("t[a-f0-9]{12}"));
		assertEquals("customers", request.getTargetName());
		assertEquals(Instant.parse("2026-06-07T00:00:00Z"), request.getTimestamp());
		assertNotNull(request.getCreateIndexer());
		assertTrue(request.getCreateIndexer().getPrefix().matches("i[a-f0-9]{12}"));
		assertTrue(request.getCreateIndexer().getIndexName().matches("customers--idx-[a-f0-9-]{36}"));
		assertTrue(request.getCreateIndexer().getQueueName().matches("customers--queue-[a-f0-9-]{36}"));
		assertEquals(InitialPublicationMode.READY, request.getCreateIndexer().getInitialPublicationMode());
	}

	@Test
	void createsIndexerRequestFromTargetRecord(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		AdminCreateRequestResolver resolver = new AdminCreateRequestResolver(repository);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(resolver::indexer)
			.onComplete(testContext.succeeding(request -> testContext.verify(() -> {
				assertTrue(request.getPrefix().matches("i[a-f0-9]{12}"));
				assertNotNull(request.getTargetId());
				assertTrue(request.getIndexName().matches("customers--idx-[a-f0-9-]{36}"));
				assertTrue(request.getQueueName().matches("customers--queue-[a-f0-9-]{36}"));
				testContext.completeNow();
			})));
	}
}

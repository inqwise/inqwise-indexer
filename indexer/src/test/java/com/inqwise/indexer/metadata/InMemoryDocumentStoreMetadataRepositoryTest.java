package com.inqwise.indexer.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerType;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryDocumentStoreMetadataRepositoryTest {
	@Test
	void insertsAndUpdatesTargetById(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(new InsertTarget("target-uid", "customers-2024", null))
			.compose(repository::getTargetById)
			.compose(found -> {
				assertTrue(found.isPresent());
				TargetRecord target = found.get();
				assertEquals("target-uid", target.uid());
				assertEquals(TargetStatus.ACTIVE, target.status());
				assertEquals(0L, target.version());
				return repository.updateTargetStatus(new UpdateTargetStatus(
					target.id(),
					TargetStatus.NON_ACTIVE,
					target.version()
				)).compose(ignored -> repository.getTargetById(target.id()));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(TargetStatus.NON_ACTIVE, found.get().status());
				assertEquals(1L, found.get().version());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsStaleTargetUpdate(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(new InsertTarget(null, "customers-2024", null))
			.compose(id -> repository.updateTargetStatus(new UpdateTargetStatus(
				id,
				TargetStatus.NON_ACTIVE,
				1L
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("version conflict"));
				testContext.completeNow();
			})));
	}

	@Test
	void ensuresConcreteTargetFromDefinitionAndUtcPeriod(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetPeriodResolver resolver = new TargetPeriodResolver();

		repository.insertTargetDefinition(new InsertTargetDefinition(
			"customers-uid",
			"customers",
			TargetPeriodStrategy.MONTHLY,
			null
		))
			.compose(repository::getTargetDefinitionById)
			.compose(found -> {
				assertTrue(found.isPresent());
				TargetPeriod period = resolver.resolve(
					found.get().periodStrategy(),
					Instant.parse("2026-05-18T10:15:00Z")
				);
				return repository.ensureTarget(found.get(), period);
			})
			.compose(target -> repository.getTargetByDefinitionAndPeriod(new ConcreteTargetKey(
				target.targetDefinitionId(),
				target.periodKey()
			)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				TargetRecord target = found.get();
				assertEquals("customers--2026-05", target.targetName());
				assertEquals("2026-05", target.periodKey());
				assertEquals(TargetProvisioningState.READY, target.provisioningState());
				testContext.completeNow();
			})));
	}

	@Test
	void listsWritablePublishedAndRuntimeActiveIndexersByForeignId(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(new InsertTarget(null, "customers-2024", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				"indexer-a",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-a",
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(firstId -> repository.insertIndexer(new InsertIndexer(
				"indexer-b",
				targetId,
				"customers-2024",
				"customers-2024-b",
				"queue-b",
				IndexerType.INDEX,
				IndexerRuntimeStatus.NON_ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.READ_ONLY
			)).compose(secondId -> assertIndexerLists(repository, targetId, firstId, secondId))))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void publicationReadinessSetsReadyAt(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertPublication(new InsertPublication(
			null,
			10,
			20,
			"customers-2024",
			"customers-2024-a",
			null,
			null
		))
			.compose(repository::getPublicationById)
			.compose(found -> {
				assertTrue(found.isPresent());
				PublicationRecord publication = found.get();
				assertEquals(ReadinessState.PENDING, publication.readinessState());
				return repository.updatePublicationReadiness(new UpdatePublicationReadiness(
					publication.id(),
					ReadinessState.READY,
					"baseline loaded",
					publication.version()
				)).compose(ignored -> repository.getPublicationByIndexerId(10));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(ReadinessState.READY, found.get().readinessState());
				assertEquals("baseline loaded", found.get().reason());
				assertNotNull(found.get().readyAt());
				testContext.completeNow();
			})));
	}

	@Test
	void allowsOnlyOneActiveManifestPerIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertManifest(new InsertManifest(
			"manifest-a",
			20,
			10,
			"customers-2024",
			"customers-2024-a",
			"customers",
			"v1",
			new JsonObject().put("collections", "customers"),
			ManifestStatus.ACTIVE
		))
			.compose(firstId -> repository.insertManifest(new InsertManifest(
				"manifest-b",
				20,
				10,
				"customers-2024",
				"customers-2024-a",
				"customers",
				"v2",
				new JsonObject(),
				ManifestStatus.ACTIVE
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("Active manifest already exists"));
				testContext.completeNow();
			})));
	}

	@Test
	void deleteRemovesRecordAndAllowsNameReuse(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(new InsertTarget(null, "customers-2024", null))
			.compose(id -> repository.deleteTarget(new DeleteTarget(id, 0L))
				.compose(ignored -> repository.getTargetById(id))
				.compose(found -> {
					assertFalse(found.isPresent());
					return repository.insertTarget(new InsertTarget(null, "customers-2024", null));
				}))
			.onComplete(testContext.succeeding(id -> testContext.verify(() -> {
				assertEquals(2, id);
				testContext.completeNow();
			})));
	}

	private Future<Void> assertIndexerLists(
		InMemoryDocumentStoreMetadataRepository repository,
		Integer targetId,
		Integer firstId,
		Integer secondId
	) {
		return repository.listIndexersByTargetId(targetId)
			.compose(all -> {
				assertEquals(2, all.size());
				assertEquals(firstId, all.get(0).id());
				assertEquals(secondId, all.get(1).id());
				return repository.listWritableIndexersByTargetId(targetId);
			})
			.compose(writable -> {
				assertEquals(1, writable.size());
				assertEquals(firstId, writable.get(0).id());
				return repository.listPublishedIndexersByTargetId(targetId);
			})
			.compose(published -> {
				assertEquals(1, published.size());
				assertEquals(firstId, published.get(0).id());
				return repository.listRuntimeActiveIndexers();
			})
			.compose(active -> {
				assertEquals(1, active.size());
				assertEquals(firstId, active.get(0).id());
				return Future.succeededFuture();
			});
	}
}

package com.inqwise.indexer.metadata;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.ReadinessState;
import com.inqwise.indexer.catalog.targets.ConcreteTargetKey;
import com.inqwise.indexer.catalog.targets.TargetNameValidator;
import com.inqwise.indexer.catalog.targets.TargetPeriod;
import com.inqwise.indexer.catalog.targets.TargetPeriodResolver;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.provisioning.ManifestStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerType;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryDocumentStoreMetadataRepositoryTest {
	@Test
	void requiresExplicitTargetPrefix() {
		NullPointerException error = assertThrows(
			NullPointerException.class,
			() -> readyTarget(null, "customers")
		);

		assertEquals("prefix", error.getMessage());
	}

	@Test
	void requiresExplicitIndexerFamilyPrefixes() {
		assertEquals("prefix", assertThrows(NullPointerException.class, () -> indexerRecord(
			null,
			1,
			"customers",
			"customers-index",
			"customers-queue",
			IndexerType.INDEX,
			IndexerRuntimeState.NON_ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		)).getMessage());
		assertEquals("prefix", assertThrows(NullPointerException.class, () -> new InsertManifest(
			null,
			1,
			1,
			"customers",
			"customers-index",
			"customers-schema",
			"1",
			new JsonObject(),
			ManifestStatus.DRAFT
		)).getMessage());
		assertEquals("prefix", assertThrows(NullPointerException.class, () -> new InsertPublication(
			null,
			1,
			1,
			"customers",
			"customers-index",
			ReadinessState.PENDING,
			null
		)).getMessage());
	}

	@Test
	void requiresExplicitIndexerClassification() {
		assertEquals("type", assertThrows(
			NullPointerException.class,
			() -> insertIndexer(null, IndexerRole.LIVE_WRITER, IndexResourceOwnership.OWNER)
		).getMessage());
		assertEquals("role", assertThrows(
			NullPointerException.class,
			() -> insertIndexer(IndexerType.INDEX, null, IndexResourceOwnership.OWNER)
		).getMessage());
		assertEquals("indexOwnership", assertThrows(
			NullPointerException.class,
			() -> insertIndexer(IndexerType.INDEX, IndexerRole.LIVE_WRITER, null)
		).getMessage());
	}

	@Test
	void insertsAndUpdatesTargetById(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(readyTarget("target-uid", "customers-2024"))
			.compose(repository::getTargetById)
			.compose(found -> {
				assertTrue(found.isPresent());
				TargetRecord target = found.get();
				assertEquals("target-uid-1", target.uid());
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

		repository.insertTarget(readyTarget("test", "customers-2024"))
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
	void rejectsNonCanonicalTargetName() {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		Future<Integer> uppercase = repository.insertTarget(readyTarget(
			"test",
			"Customers"
		));
		assertTrue(uppercase.failed());
		assertEquals("Target name is not canonical: Customers", uppercase.cause().getMessage());

		Future<Integer> spaces = repository.insertTarget(readyTarget(
			"test",
			"customer docs"
		));
		assertTrue(spaces.failed());
		assertEquals("Target name is not canonical: customer docs", spaces.cause().getMessage());
	}

	@Test
	void rejectsTargetNameOverLimit() {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		String targetName = "a".repeat(TargetNameValidator.MAX_TARGET_NAME_LENGTH + 1);

		Future<Integer> inserted = repository.insertTarget(readyTarget(
			"test",
			targetName
		));
		assertTrue(inserted.failed());
		assertEquals("Target name is too long: 129", inserted.cause().getMessage());
	}

	@Test
	void ensuresConcreteTargetFromNameAndUtcPeriod(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetPeriodResolver resolver = new TargetPeriodResolver();

		TargetPeriod period = resolver.resolve(
			TargetPeriodStrategy.MONTHLY,
			Instant.parse("2026-05-18T10:15:00Z")
		);

		repository.ensureTarget("customers", period)
			.compose(target -> repository.getTargetByDefinitionAndPeriod(new ConcreteTargetKey(
				target.targetName(),
				target.periodKey()
			)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				TargetRecord target = found.get();
				assertEquals("customers", target.targetName());
				assertEquals("2026-05", target.periodKey());
				assertEquals(TargetProvisioningState.READY, target.provisioningState());
				testContext.completeNow();
			})));
	}

	@Test
	void listsWritablePublishedAndRuntimeActiveIndexersByForeignId(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(readyTarget("test", "customers-2024"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"indexer-a",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-a",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(firstId -> repository.insertIndexer(indexerRecord(
				"indexer-b",
				targetId,
				"customers-2024",
				"customers-2024-b",
				"queue-b",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.READ_ONLY
			)).compose(secondId -> assertIndexerLists(repository, targetId, firstId, secondId))))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void allowsLoadAndLiveWritersToSharePhysicalIndex(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(readyTarget("test", "customers-2024"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"load-writer",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(loadId -> repository.insertIndexer(indexerRecord(
				"live-writer",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-live",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.ATTACHED,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(liveId -> repository.listIndexersByTargetId(targetId))))
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(2, indexers.size());
				assertEquals("customers-2024-a", indexers.get(0).indexName());
				assertEquals("customers-2024-a", indexers.get(1).indexName());
				assertEquals(IndexerRole.LOAD_WRITER, indexers.get(0).role());
				assertEquals(IndexerRole.LIVE_WRITER, indexers.get(1).role());
				assertEquals(IndexResourceOwnership.OWNER, indexers.get(0).indexOwnership());
				assertEquals(IndexResourceOwnership.ATTACHED, indexers.get(1).indexOwnership());
				testContext.completeNow();
			})));
	}

	@Test
	void listIndexersFiltersByRole(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(readyTarget("test", "customers-2024"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"load-writer",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(loadId -> repository.insertIndexer(indexerRecord(
				"live-writer",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-live",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.ATTACHED,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(liveId -> repository.listIndexers(new IndexerMetadataQuery(
				null,
				null,
				null,
				List.of(IndexerRole.LIVE_WRITER),
				null,
				null,
				null,
				null,
				null
			)))))
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals(IndexerRole.LIVE_WRITER, indexers.get(0).role());
				testContext.completeNow();
			})));
	}

	@Test
	void updatesIndexerQueueNameWithExpectedVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(readyTarget("test", "customers-2024"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"test",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-a",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> repository.updateIndexerQueueName(new UpdateIndexerQueueName(
				indexerId,
				"queue-a-v1",
				0L
			)).compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-a-v1", found.get().queueName());
				assertEquals(1L, found.get().version());
				assertEquals(IndexerRuntimeState.ACTIVE, found.get().runtimeState());
				assertEquals(PublicationState.PUBLISHED, found.get().publicationState());
				assertEquals(MutationState.WRITABLE, found.get().mutationState());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsStaleIndexerQueueNameUpdate(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(readyTarget("test", "customers-2024"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"test",
				targetId,
				"customers-2024",
				"customers-2024-a",
				"queue-a",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> repository.updateIndexerQueueName(new UpdateIndexerQueueName(
				indexerId,
				"queue-a-v2",
				2L
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("version conflict"));
				testContext.completeNow();
			})));
	}

	@Test
	void publicationReadinessSetsReadyAt(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertPublication(new InsertPublication(
			"test",
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

		repository.insertTarget(readyTarget("test", "customers-2024"))
			.compose(id -> repository.deleteTarget(new DeleteTarget(id, 0L))
				.compose(ignored -> repository.getTargetById(id))
				.compose(found -> {
					assertFalse(found.isPresent());
					return repository.insertTarget(readyTarget("test", "customers-2024"));
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

	private InsertIndexer insertIndexer(
		IndexerType type,
		IndexerRole role,
		IndexResourceOwnership indexOwnership
	) {
		return indexerRecord(
			"test",
			1,
			"customers",
			"customers-index",
			"customers-queue",
			type,
			role,
			indexOwnership,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY,
			IndexerRuntimeState.NON_ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		);
	}
}

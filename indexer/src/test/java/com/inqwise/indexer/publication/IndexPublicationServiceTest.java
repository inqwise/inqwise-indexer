package com.inqwise.indexer.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertPublication;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;
import com.inqwise.indexer.metadata.UpdatePublicationReadiness;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexPublicationServiceTest {
	@Test
	void markIndexReadyUpdatesPublicationReadiness(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublication(repository, ReadinessState.PENDING)
			.compose(publicationId -> publicationService.markReady(new MarkIndexReadyRequest(
				publicationId,
				"baseline loaded",
				0L
			)).compose(result -> {
				assertEquals(publicationId, result.publicationId());
				assertEquals(ReadinessState.READY, result.readinessState());
				assertEquals(1L, result.version());
				return repository.getPublicationById(publicationId);
			}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(ReadinessState.READY, found.get().readinessState());
				assertEquals("baseline loaded", found.get().reason());
				assertNotNull(found.get().readyAt());
				testContext.completeNow();
			})));
	}

	@Test
	void exactMarkReadyRedeliveryDoesNotAdvanceVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublication(repository, ReadinessState.PENDING)
			.compose(publicationId -> {
				MarkIndexReadyRequest ready = new MarkIndexReadyRequest(
					publicationId,
					"baseline loaded",
					0L
				);
				return publicationService.markReady(ready)
					.compose(ignored -> publicationService.markReady(ready))
					.compose(ignored -> repository.getPublicationById(publicationId));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(ReadinessState.READY, found.get().readinessState());
				assertEquals("baseline loaded", found.get().reason());
				assertEquals(1L, found.get().version());
				testContext.completeNow();
			})));
	}

	@Test
	void markReadyRedeliveryRejectsDifferentReason(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublication(repository, ReadinessState.PENDING)
			.compose(publicationId -> publicationService.markReady(new MarkIndexReadyRequest(
				publicationId,
				"baseline loaded",
				0L
			)).compose(ignored -> publicationService.markReady(new MarkIndexReadyRequest(
				publicationId,
				"different completion",
				0L
			))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith(
					"Publication version conflict for id"
				));
				testContext.completeNow();
			})));
	}

	@Test
	void markReadyRedeliveryRejectsLaterVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublication(repository, ReadinessState.PENDING)
			.compose(publicationId -> publicationService.markReady(new MarkIndexReadyRequest(
				publicationId,
				"baseline loaded",
				0L
			)).compose(ignored -> repository.updatePublicationReadiness(
				new UpdatePublicationReadiness(
					publicationId,
					ReadinessState.PENDING,
					"reopened",
					1L
				)
			)).compose(ignored -> publicationService.markReady(new MarkIndexReadyRequest(
				publicationId,
				"baseline loaded",
				0L
			))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith(
					"Publication version conflict for id"
				));
				testContext.completeNow();
			})));
	}

	@Test
	void publishFailsWhenIndexIsNotReady(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.PENDING)
			.compose(indexerId -> publicationService.publish(new PublishIndexRequest(indexerId, 0L)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Index is not ready"));
				testContext.completeNow();
			})));
	}

	@Test
	void publishChangesIndexerPublicationState(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentResources documentResources = new RecordingDocumentResources();
		RecordingQueueResources queueResources = new RecordingQueueResources();
		IndexPublicationService publicationService = publicationService(
			repository,
			documentResources,
			queueResources
		);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.READY)
			.compose(indexerId -> publicationService.publish(new PublishIndexRequest(indexerId, 0L))
				.compose(result -> {
					assertEquals(indexerId, result.indexerId());
					assertEquals(PublicationState.PUBLISHED, result.publicationState());
					assertEquals(1L, result.version());
					return repository.getIndexerById(indexerId);
				}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.PUBLISHED, found.get().publicationState());
				assertEquals(1L, found.get().version());
				assertEquals(List.of("customers_1"), documentResources.ensured);
				assertEquals(List.of("queue-customers"), queueResources.ensured);
				testContext.completeNow();
			})));
	}

	@Test
	void publishLeavesMetadataUnpublishedWhenResourceCheckFails(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentResources documentResources = new RecordingDocumentResources();
		documentResources.failure = new IllegalStateException("index unavailable");
		IndexPublicationService publicationService = publicationService(
			repository,
			documentResources,
			new RecordingQueueResources()
		);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.READY)
			.compose(indexerId -> publicationService.publish(new PublishIndexRequest(indexerId, 0L))
				.transform(result -> repository.getIndexerById(indexerId).map(found -> {
					assertTrue(result.failed());
					assertEquals("index unavailable", result.cause().getMessage());
					return found;
				})))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.UNPUBLISHED, found.get().publicationState());
				assertEquals(0L, found.get().version());
				testContext.completeNow();
			})));
	}

	@Test
	void exactPublishRedeliveryRechecksResourcesWithoutAdvancingVersion(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentResources documentResources = new RecordingDocumentResources();
		RecordingQueueResources queueResources = new RecordingQueueResources();
		IndexPublicationService publicationService = publicationService(
			repository,
			documentResources,
			queueResources
		);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.READY)
			.compose(indexerId -> {
				PublishIndexRequest publish = new PublishIndexRequest(indexerId, 0L);
				return publicationService.publish(publish)
					.compose(ignored -> publicationService.publish(publish))
					.compose(ignored -> repository.getIndexerById(indexerId));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.PUBLISHED, found.get().publicationState());
				assertEquals(1L, found.get().version());
				assertEquals(List.of("customers_1", "customers_1"), documentResources.ensured);
				assertEquals(List.of("queue-customers", "queue-customers"), queueResources.ensured);
				testContext.completeNow();
			})));
	}

	@Test
	void publishRedeliveryRejectsLaterRetiredState(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.READY)
			.compose(indexerId -> publicationService.publish(new PublishIndexRequest(indexerId, 0L))
				.compose(ignored -> publicationService.retire(new RetireIndexRequest(indexerId, 1L)))
				.compose(ignored -> publicationService.publish(new PublishIndexRequest(indexerId, 0L))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith(
					"Indexer version conflict for id"
				));
				testContext.completeNow();
			})));
	}

	@Test
	void publishFailsWhenIndexerIsDeleting(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublishableIndexer(repository, MutationState.DELETING, ReadinessState.READY)
			.compose(indexerId -> publicationService.publish(new PublishIndexRequest(indexerId, 0L)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Index is deleting"));
				testContext.completeNow();
			})));
	}

	@Test
	void retireChangesPublicationStateToRetired(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublishedIndexer(repository)
			.compose(indexerId -> publicationService.retire(new RetireIndexRequest(indexerId, 0L))
				.compose(result -> {
					assertEquals(indexerId, result.indexerId());
					assertEquals(PublicationState.RETIRED, result.publicationState());
					assertEquals(1L, result.version());
					return repository.getIndexerById(indexerId);
				}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.RETIRED, found.get().publicationState());
				testContext.completeNow();
			})));
	}

	@Test
	void exactRetireRedeliveryDoesNotAdvanceVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublishedIndexer(repository)
			.compose(indexerId -> {
				RetireIndexRequest retire = new RetireIndexRequest(indexerId, 0L);
				return publicationService.retire(retire)
					.compose(ignored -> publicationService.retire(retire))
					.compose(ignored -> repository.getIndexerById(indexerId));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.RETIRED, found.get().publicationState());
				assertEquals(1L, found.get().version());
				testContext.completeNow();
			})));
	}

	@Test
	void retireRedeliveryRejectsLaterVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexPublicationService publicationService = publicationService(repository);

		insertPublishedIndexer(repository)
			.compose(indexerId -> publicationService.retire(new RetireIndexRequest(indexerId, 0L))
				.compose(ignored -> repository.updateIndexerRuntimeState(
					new UpdateIndexerRuntimeState(
						indexerId,
						IndexerRuntimeState.NON_ACTIVE,
						1L
					)
				))
				.compose(ignored -> publicationService.retire(new RetireIndexRequest(indexerId, 0L))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith(
					"Indexer version conflict for id"
				));
				testContext.completeNow();
			})));
	}

	private IndexPublicationService publicationService(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		return publicationService(
			repository,
			new RecordingDocumentResources(),
			new RecordingQueueResources()
		);
	}

	private IndexPublicationService publicationService(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerDocumentIndexResourceManager documentResources,
		IndexerQueueResourceManager queueResources
	) {
		IndexPublicationService publicationService = new MetadataIndexPublicationService(
			repository,
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("customers", "v1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			)),
			documentResources,
			queueResources
		);
		return publicationService;
	}

	private Future<Integer> insertPublication(
		InMemoryDocumentStoreMetadataRepository repository,
		ReadinessState readinessState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> repository.insertPublication(new InsertPublication(
				null,
				indexerId,
				targetId,
				"customers",
				"customers_1",
				readinessState,
				null
			))));
	}

	private Future<Integer> insertPublishableIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		MutationState mutationState,
		ReadinessState readinessState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				mutationState
			)).compose(indexerId -> repository.insertPublication(new InsertPublication(
				null,
				indexerId,
				targetId,
				"customers",
				"customers_1",
				readinessState,
				null
			)).map(indexerId)));
	}

	private Future<Integer> insertPublishedIndexer(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.READ_ONLY
			)));
	}

	private static class RecordingDocumentResources
		implements IndexerDocumentIndexResourceManager {
		private final List<String> ensured = new ArrayList<>();
		private Throwable failure;

		@Override
		public Future<Void> ensure(String indexName, IndexDefinition definition) {
			ensured.add(indexName);
			return failure == null ? Future.succeededFuture() : Future.failedFuture(failure);
		}

		@Override
		public Future<Void> delete(String indexName) {
			return Future.succeededFuture();
		}
	}

	private static class RecordingQueueResources implements IndexerQueueResourceManager {
		private final List<String> ensured = new ArrayList<>();

		@Override
		public Future<Void> ensure(String queueName) {
			ensured.add(queueName);
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String queueName) {
			return Future.succeededFuture();
		}
	}
}

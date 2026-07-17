package com.inqwise.indexer.metadata;

import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryDocumentStoreMetadataOwnershipTest {
	@Test
	void deletingQueueIdentityCannotChange(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(indexer(
				targetId,
				"customers-index",
				"customers-queue",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				PublicationState.UNPUBLISHED,
				true
			)))
			.compose(indexerId -> repository.updateIndexerQueueName(
				new UpdateIndexerQueueName(indexerId, "changed-queue", 0L)
			).transform(result -> {
				assertTrue(result.failed());
				assertTrue(result.cause().getMessage().contains("immutable"));
				return repository.getIndexerById(indexerId);
			}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("customers-queue", found.get().queueName());
				testContext.completeNow();
			})));
	}

	@Test
	void deletingMutationStateCannotBeReopened(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(indexer(
				targetId,
				"customers-index",
				"customers-queue",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				PublicationState.UNPUBLISHED,
				true
			)))
			.compose(indexerId -> repository.updateIndexerMutationState(
				new UpdateIndexerMutationState(indexerId, MutationState.WRITABLE, 0L)
			).transform(result -> {
				assertTrue(result.failed());
				assertTrue(result.cause().getMessage().contains("terminal"));
				return repository.getIndexerById(indexerId);
			}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(MutationState.DELETING, found.get().mutationState());
				testContext.completeNow();
			})));
	}

	@Test
	void replacementRejectsEveryDeletingParticipant(VertxTestContext testContext) {
		Future.all(
			assertDeletingParticipantRejected(Participant.CANDIDATE),
			assertDeletingParticipantRejected(Participant.PREVIOUS),
			assertDeletingParticipantRejected(Participant.OWNERSHIP_SOURCE)
		).onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private Future<Void> assertDeletingParticipantRejected(Participant deletingParticipant) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();

		return insertFixture(repository, deletingParticipant)
			.compose(fixture -> repository.replacePublishedIndexer(new ReplacePublishedIndexer(
				fixture.targetId(),
				fixture.candidateId(),
				0L,
				fixture.previousId(),
				0L,
				fixture.ownershipSourceId(),
				0L
			)).transform(result -> {
				assertTrue(result.failed());
				assertTrue(result.cause().getMessage().contains(deletingParticipant.label));
				return repository.listIndexersByTargetId(fixture.targetId());
			}).map(indexers -> {
				IndexerRecord previous = find(indexers, IndexerRole.LIVE_WRITER,
					indexer -> indexer.publicationState() == PublicationState.PUBLISHED);
				IndexerRecord candidate = find(indexers, IndexerRole.LIVE_WRITER,
					indexer -> indexer.indexOwnership() == IndexResourceOwnership.ATTACHED);
				IndexerRecord ownershipSource = find(indexers, IndexerRole.LOAD_WRITER,
					indexer -> true);
				assertEquals(IndexResourceOwnership.OWNER, previous.indexOwnership());
				assertEquals(PublicationState.PUBLISHED, previous.publicationState());
				assertEquals(IndexResourceOwnership.ATTACHED, candidate.indexOwnership());
				assertEquals(PublicationState.UNPUBLISHED, candidate.publicationState());
				assertEquals(IndexResourceOwnership.OWNER, ownershipSource.indexOwnership());
				return null;
			}));
	}

	private Future<Fixture> insertFixture(
		InMemoryDocumentStoreMetadataRepository repository,
		Participant deletingParticipant
	) {
		return repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(indexer(
				targetId,
				"old-index",
				"previous-queue",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				PublicationState.PUBLISHED,
				deletingParticipant == Participant.PREVIOUS
			)).compose(previousId -> repository.insertIndexer(indexer(
				targetId,
				"new-index",
				"candidate-queue",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.ATTACHED,
				PublicationState.UNPUBLISHED,
				deletingParticipant == Participant.CANDIDATE
			)).compose(candidateId -> repository.insertIndexer(indexer(
				targetId,
				"new-index",
				"source-queue",
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				PublicationState.UNPUBLISHED,
				deletingParticipant == Participant.OWNERSHIP_SOURCE
			)).map(ownershipSourceId -> new Fixture(
				targetId,
				previousId,
				candidateId,
				ownershipSourceId
			)))));
	}

	private InsertIndexer indexer(
		Integer targetId,
		String indexName,
		String queueName,
		IndexerRole role,
		IndexResourceOwnership ownership,
		PublicationState publicationState,
		boolean deleting
	) {
		return new InsertIndexer(
			null,
			targetId,
			"customers",
			indexName,
			queueName,
			IndexerType.INDEX,
			role,
			ownership,
			IndexerRuntimeState.NON_ACTIVE,
			publicationState,
			deleting ? MutationState.DELETING : MutationState.WRITABLE
		);
	}

	private IndexerRecord find(
		java.util.List<IndexerRecord> indexers,
		IndexerRole role,
		Function<IndexerRecord, Boolean> predicate
	) {
		return indexers.stream()
			.filter(indexer -> indexer.role() == role)
			.filter(predicate::apply)
			.findFirst()
			.orElseThrow();
	}

	private enum Participant {
		CANDIDATE("Candidate"),
		PREVIOUS("Previous"),
		OWNERSHIP_SOURCE("Ownership source");

		private final String label;

		Participant(String label) {
			this.label = label;
		}
	}

	private record Fixture(
		Integer targetId,
		Integer previousId,
		Integer candidateId,
		Integer ownershipSourceId
	) {
	}
}

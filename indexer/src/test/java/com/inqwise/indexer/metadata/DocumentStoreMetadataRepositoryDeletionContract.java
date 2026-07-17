package com.inqwise.indexer.metadata;

import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.provisioning.ManifestStatus;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.ReadinessState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.errors.RetryableStaleStateException;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

abstract class DocumentStoreMetadataRepositoryDeletionContract {
	abstract DocumentStoreMetadataRepository createRepository();

	@Test
	void finalizationRemovesIndexerAndDependentMetadataAtomically(
		VertxTestContext testContext
	) {
		DocumentStoreMetadataRepository repository = createRepository();

		insertFixture(repository, MutationState.DELETING)
			.compose(fixture -> repository.finalizeIndexerDeletion(
				new FinalizeIndexerDeletion(fixture.indexerId(), 0L)
			).compose(ignored -> assertFixtureAbsent(repository, fixture)))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void finalizationTreatsMissingIndexerAsIdempotentSuccess(
		VertxTestContext testContext
	) {
		createRepository().finalizeIndexerDeletion(new FinalizeIndexerDeletion(404, 9L))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void finalizationRejectsStaleVersionWithoutDeletingMetadata(
		VertxTestContext testContext
	) {
		DocumentStoreMetadataRepository repository = createRepository();

		insertFixture(repository, MutationState.DELETING)
			.compose(fixture -> repository.finalizeIndexerDeletion(
				new FinalizeIndexerDeletion(fixture.indexerId(), 1L)
			).transform(result -> {
				assertTrue(result.failed());
				assertInstanceOf(RetryableStaleStateException.class, result.cause());
				return assertFixturePresent(repository, fixture);
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void finalizationRejectsNonDeletingIndexerWithoutDeletingMetadata(
		VertxTestContext testContext
	) {
		DocumentStoreMetadataRepository repository = createRepository();

		insertFixture(repository, MutationState.WRITABLE)
			.compose(fixture -> repository.finalizeIndexerDeletion(
				new FinalizeIndexerDeletion(fixture.indexerId(), 0L)
			).transform(result -> {
				assertTrue(result.failed());
				assertInstanceOf(IllegalStateException.class, result.cause());
				return assertFixturePresent(repository, fixture);
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void concurrentFinalizationCallsAreIdempotent(VertxTestContext testContext) {
		DocumentStoreMetadataRepository repository = createRepository();

		insertFixture(repository, MutationState.DELETING)
			.compose(fixture -> Future.all(
				repository.finalizeIndexerDeletion(
					new FinalizeIndexerDeletion(fixture.indexerId(), 0L)
				),
				repository.finalizeIndexerDeletion(
					new FinalizeIndexerDeletion(fixture.indexerId(), 0L)
				)
			).compose(ignored -> assertFixtureAbsent(repository, fixture)))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private Future<Fixture> insertFixture(
		DocumentStoreMetadataRepository repository,
		MutationState mutationState
	) {
		return repository.insertTarget(new InsertTarget("test", "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				"test",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.UNPUBLISHED,
				mutationState
			)).compose(indexerId -> repository.insertPublication(new InsertPublication(
				"test",
				indexerId,
				targetId,
				"customers",
				"customers-index",
				ReadinessState.PENDING,
				null
			)).compose(publicationId -> repository.insertManifest(new InsertManifest(
				"test",
				targetId,
				indexerId,
				"customers",
				"customers-index",
				"customers-schema",
				"1",
				new JsonObject(),
				ManifestStatus.ACTIVE
			)).compose(activeManifestId -> repository.insertManifest(new InsertManifest(
				"test",
				targetId,
				indexerId,
				"customers",
				"customers-index",
				"customers-schema",
				"2",
				new JsonObject(),
				ManifestStatus.DRAFT
			)).map(draftManifestId -> new Fixture(
				targetId,
				indexerId,
				publicationId,
				activeManifestId,
				draftManifestId
			))))));
	}

	private Future<Void> assertFixturePresent(
		DocumentStoreMetadataRepository repository,
		Fixture fixture
	) {
		return Future.all(
			repository.getIndexerById(fixture.indexerId()),
			repository.getPublicationById(fixture.publicationId()),
			repository.getManifestById(fixture.activeManifestId()),
			repository.getManifestById(fixture.draftManifestId())
		).map(results -> {
			assertTrue(results.<java.util.Optional<IndexerRecord>>resultAt(0).isPresent());
			assertTrue(results.<java.util.Optional<PublicationRecord>>resultAt(1).isPresent());
			assertTrue(results.<java.util.Optional<ManifestRecord>>resultAt(2).isPresent());
			assertTrue(results.<java.util.Optional<ManifestRecord>>resultAt(3).isPresent());
			return null;
		});
	}

	private Future<Void> assertFixtureAbsent(
		DocumentStoreMetadataRepository repository,
		Fixture fixture
	) {
		return Future.all(
			repository.getIndexerById(fixture.indexerId()),
			repository.getPublicationById(fixture.publicationId()),
			repository.getManifestById(fixture.activeManifestId()),
			repository.getManifestById(fixture.draftManifestId()),
			repository.listManifestsByTargetId(fixture.targetId())
		).map(results -> {
			assertTrue(results.<java.util.Optional<IndexerRecord>>resultAt(0).isEmpty());
			assertTrue(results.<java.util.Optional<PublicationRecord>>resultAt(1).isEmpty());
			assertTrue(results.<java.util.Optional<ManifestRecord>>resultAt(2).isEmpty());
			assertTrue(results.<java.util.Optional<ManifestRecord>>resultAt(3).isEmpty());
			assertEquals(0, results.<java.util.List<ManifestRecord>>resultAt(4).size());
			return null;
		});
	}

	private record Fixture(
		Integer targetId,
		Integer indexerId,
		Integer publicationId,
		Integer activeManifestId,
		Integer draftManifestId
	) {
	}
}

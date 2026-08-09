package com.inqwise.indexer.metadata;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexQuery;
import com.inqwise.indexer.provisioning.ManifestStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RepositoryPublishedIndexResolverTest {
	private static final Instant JANUARY = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant FEBRUARY = Instant.parse("2026-02-01T00:00:00Z");
	private static final Instant MARCH = Instant.parse("2026-03-01T00:00:00Z");
	private static final Instant APRIL = Instant.parse("2026-04-01T00:00:00Z");
	private static final String SCHEMA_NAME = "customer";
	private static final String SCHEMA_VERSION = "v1";

	@Test
	void resolvesSparsePeriodsInPeriodOrder(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		insertTarget(repository, "2026-03", MARCH, APRIL)
			.compose(marchTarget -> insertIndexer(repository, marchTarget, "customers_2026_03")
				.map(marchIndexer -> new int[] { marchTarget, marchIndexer }))
			.compose(march -> insertTarget(repository, "2026-01", JANUARY, FEBRUARY)
				.compose(januaryTarget -> insertIndexer(repository, januaryTarget, "customers_2026_01")
					.compose(januaryIndexer -> resolver.resolvePublishedIndexes(
						new PublishedIndexQuery("customers", JANUARY, APRIL)
					).map(indexes -> {
						assertEquals(List.of(
							new PublishedIndex(
								januaryIndexer,
								januaryTarget,
								"customers_2026_01",
								SCHEMA_NAME,
								SCHEMA_VERSION
							),
							new PublishedIndex(
								march[1],
								march[0],
								"customers_2026_03",
								SCHEMA_NAME,
								SCHEMA_VERSION
							)
						), indexes);
						return null;
					}))))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void resolvesUnperiodizedTargetForAnyRange(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> insertIndexer(repository, targetId, "customers_all")
				.compose(indexerId -> resolver.resolvePublishedIndexes(
					new PublishedIndexQuery("customers", JANUARY, FEBRUARY)
				).map(indexes -> {
					assertEquals(List.of(
						new PublishedIndex(
							indexerId,
							targetId,
							"customers_all",
							SCHEMA_NAME,
							SCHEMA_VERSION
						)
					), indexes);
					return null;
				})))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void excludesTargetsAndIndexersThatAreNotQuerySafe(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		insertTarget(repository, "2026-01", JANUARY, FEBRUARY)
			.compose(targetId -> Future.all(
				insertIndexer(repository, targetId, "published"),
				insertIndexer(repository, targetId, "unpublished", PublicationState.UNPUBLISHED,
					MutationState.WRITABLE, IndexerStatus.AVAILABLE, IndexerProvisioningState.READY),
				insertIndexer(repository, targetId, "deleting", PublicationState.PUBLISHED,
					MutationState.DELETING, IndexerStatus.AVAILABLE, IndexerProvisioningState.READY),
				insertIndexer(repository, targetId, "failed", PublicationState.PUBLISHED,
					MutationState.WRITABLE, IndexerStatus.AVAILABLE, IndexerProvisioningState.FAILED)
			).compose(ignored -> resolver.resolvePublishedIndexes(
				new PublishedIndexQuery("customers", JANUARY, FEBRUARY)
			)))
			.onComplete(testContext.succeeding(indexes -> testContext.verify(() -> {
				assertEquals(1, indexes.size());
				assertEquals("published", indexes.get(0).indexName());
				testContext.completeNow();
			})));
	}

	@Test
	void returnsEmptyWhenNoPeriodOverlaps(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		insertTarget(repository, "2026-01", JANUARY, FEBRUARY)
			.compose(targetId -> insertIndexer(repository, targetId, "customers_2026_01"))
			.compose(ignored -> resolver.resolvePublishedIndexes(
				new PublishedIndexQuery("customers", MARCH, APRIL)
			))
			.onComplete(testContext.succeeding(indexes -> testContext.verify(() -> {
				assertTrue(indexes.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsPublishedIndexerWithoutActiveManifest(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		insertTarget(repository, "2026-01", JANUARY, FEBRUARY)
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"test",
				targetId,
				"customers",
				"customers_2026_01",
				"queue-customers_2026_01",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerStatus.AVAILABLE,
				IndexerProvisioningState.READY,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(ignored -> resolver.resolvePublishedIndexes(
				new PublishedIndexQuery("customers", JANUARY, FEBRUARY)
			))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("no active manifest"));
				testContext.completeNow();
			})));
	}

	@Test
	void validatesLogicalQuery() {
		RepositoryPublishedIndexResolver resolver =
			new RepositoryPublishedIndexResolver(new InMemoryDocumentStoreMetadataRepository());

		assertThrows(NullPointerException.class, () -> resolver.resolvePublishedIndexes(null));
		assertThrows(IllegalArgumentException.class,
			() -> new PublishedIndexQuery("", JANUARY, FEBRUARY));
		assertThrows(NullPointerException.class,
			() -> new PublishedIndexQuery("customers", null, FEBRUARY));
		assertThrows(NullPointerException.class,
			() -> new PublishedIndexQuery("customers", JANUARY, null));
		assertThrows(IllegalArgumentException.class,
			() -> new PublishedIndexQuery("customers", JANUARY, JANUARY));
		assertThrows(IllegalArgumentException.class,
			() -> new PublishedIndexQuery("customers", FEBRUARY, JANUARY));
	}

	private Future<Integer> insertTarget(
		InMemoryDocumentStoreMetadataRepository repository,
		String periodKey,
		Instant start,
		Instant end
	) {
		return repository.insertTarget(new InsertTarget(
			"test",
			"customers",
			periodKey,
			start,
			end,
			TargetStatus.ACTIVE,
			TargetProvisioningState.READY
		));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		Integer targetId,
		String indexName
	) {
		return insertIndexer(
			repository,
			targetId,
			indexName,
			PublicationState.PUBLISHED,
			MutationState.WRITABLE,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY
		);
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		Integer targetId,
		String indexName,
		PublicationState publicationState,
		MutationState mutationState,
		IndexerStatus status,
		IndexerProvisioningState provisioningState
	) {
		return repository.insertIndexer(indexerRecord(
			"test",
			targetId,
			"customers",
			indexName,
			"queue-" + indexName,
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			status,
			provisioningState,
			IndexerRuntimeState.ACTIVE,
			publicationState,
			mutationState
		)).compose(indexerId -> repository.insertManifest(InsertManifest.builder()
			.withPrefix("test")
			.withTargetId(targetId)
			.withIndexerId(indexerId)
			.withTargetName("customers")
			.withIndexName(indexName)
			.withSchemaName(SCHEMA_NAME)
			.withSchemaVersion(SCHEMA_VERSION)
			.withStatus(ManifestStatus.ACTIVE)
			.build()).map(indexerId));
	}
}

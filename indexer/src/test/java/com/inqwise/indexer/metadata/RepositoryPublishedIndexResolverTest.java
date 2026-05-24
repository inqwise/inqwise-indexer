package com.inqwise.indexer.metadata;

import com.inqwise.indexer.IndexerRuntimeState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.inqwise.indexer.IndexerType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RepositoryPublishedIndexResolverTest {
	@Test
	void resolvesOnlyPublishedQuerySafeIndexesInIdOrder(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> {
				int[] ids = new int[4];
				return insertIndexer(
					repository,
					targetId,
					"customers_1",
					PublicationState.PUBLISHED,
					MutationState.WRITABLE,
					IndexerRuntimeState.ACTIVE
				).compose(firstId -> {
					ids[0] = firstId;
					return insertIndexer(
						repository,
						targetId,
						"customers_2",
						PublicationState.UNPUBLISHED,
						MutationState.WRITABLE,
						IndexerRuntimeState.ACTIVE
					);
				}).compose(ignored -> insertIndexer(
					repository,
					targetId,
					"customers_3",
					PublicationState.PUBLISHED,
					MutationState.DELETING,
					IndexerRuntimeState.ACTIVE
				)).compose(ignored -> insertIndexer(
					repository,
					targetId,
					"customers_4",
					PublicationState.PUBLISHED,
					MutationState.READ_ONLY,
					IndexerRuntimeState.ACTIVE
				)).compose(fourthId -> {
					ids[1] = fourthId;
					return insertIndexer(
						repository,
						targetId,
						"customers_5",
						PublicationState.PUBLISHED,
						MutationState.WRITABLE,
						IndexerRuntimeState.NON_ACTIVE
					);
				}).compose(fifthId -> {
					ids[2] = fifthId;
					return insertIndexer(
						repository,
						targetId,
						"customers_6",
						PublicationState.PUBLISHED,
						MutationState.WRITABLE,
						IndexerRuntimeState.NON_ACTIVE
					);
				}).compose(sixthId -> {
					ids[3] = sixthId;
					return resolver.resolvePublishedIndexes(targetId);
				}).map(indexes -> {
					assertEquals(4, indexes.size());
					assertEquals(new PublishedIndex(ids[0], targetId, "customers_1"), indexes.get(0));
					assertEquals(new PublishedIndex(ids[1], targetId, "customers_4"), indexes.get(1));
					assertEquals(new PublishedIndex(ids[2], targetId, "customers_5"), indexes.get(2));
					assertEquals(new PublishedIndex(ids[3], targetId, "customers_6"), indexes.get(3));
					return null;
				});
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void returnsEmptyListWhenTargetHasNoPublishedIndexes(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> insertIndexer(
				repository,
				targetId,
				"customers_1",
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE,
				IndexerRuntimeState.ACTIVE
			).compose(ignored -> resolver.resolvePublishedIndexes(targetId)))
			.onComplete(testContext.succeeding(indexes -> testContext.verify(() -> {
				assertTrue(indexes.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void resolvesMultiplePublishedIndexesForSameTarget(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RepositoryPublishedIndexResolver resolver = new RepositoryPublishedIndexResolver(repository);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> insertIndexer(
				repository,
				targetId,
				"customers_1",
				PublicationState.PUBLISHED,
				MutationState.WRITABLE,
				IndexerRuntimeState.ACTIVE
			).compose(firstId -> insertIndexer(
				repository,
				targetId,
				"customers_2",
				PublicationState.PUBLISHED,
				MutationState.READ_ONLY,
				IndexerRuntimeState.ACTIVE
			).compose(secondId -> resolver.resolvePublishedIndexes(targetId)
				.compose(indexes -> {
					assertEquals(List.of(
						new PublishedIndex(firstId, targetId, "customers_1"),
						new PublishedIndex(secondId, targetId, "customers_2")
					), indexes);
					return Future.succeededFuture();
				}))))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void rejectsMissingTargetId() {
		RepositoryPublishedIndexResolver resolver =
			new RepositoryPublishedIndexResolver(new InMemoryDocumentStoreMetadataRepository());

		assertThrows(NullPointerException.class, () -> resolver.resolvePublishedIndexes(null));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		Integer targetId,
		String indexName,
		PublicationState publicationState,
		MutationState mutationState,
		IndexerRuntimeState runtimeStatus
	) {
		return repository.insertIndexer(new InsertIndexer(
			null,
			targetId,
			"customers",
			indexName,
			"queue-" + indexName,
			IndexerType.INDEX,
			runtimeStatus,
			publicationState,
			mutationState
		));
	}
}

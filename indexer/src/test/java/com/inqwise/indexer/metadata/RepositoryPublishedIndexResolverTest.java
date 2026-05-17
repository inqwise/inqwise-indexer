package com.inqwise.indexer.metadata;

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
			.compose(targetId -> insertIndexer(
				repository,
				targetId,
				"customers_1",
				PublicationState.PUBLISHED,
				MutationState.WRITABLE,
				IndexerRuntimeStatus.STARTED
			).compose(firstId -> insertIndexer(
				repository,
				targetId,
				"customers_2",
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE,
				IndexerRuntimeStatus.STARTED
			).compose(ignored -> insertIndexer(
				repository,
				targetId,
				"customers_3",
				PublicationState.PUBLISHED,
				MutationState.DELETING,
				IndexerRuntimeStatus.STARTED
			)).compose(ignored -> insertIndexer(
				repository,
				targetId,
				"customers_4",
				PublicationState.PUBLISHED,
				MutationState.READ_ONLY,
				IndexerRuntimeStatus.COMPLETED
			)).compose(fourthId -> insertIndexer(
				repository,
				targetId,
				"customers_5",
				PublicationState.PUBLISHED,
				MutationState.WRITABLE,
				IndexerRuntimeStatus.NON_ACTIVE
			).compose(ignored -> insertIndexer(
				repository,
				targetId,
				"customers_6",
				PublicationState.PUBLISHED,
				MutationState.WRITABLE,
				IndexerRuntimeStatus.DELETED
			)).compose(ignored -> resolver.resolvePublishedIndexes(targetId))
				.compose(indexes -> {
					assertEquals(2, indexes.size());
					assertEquals(new PublishedIndex(firstId, targetId, "customers_1"), indexes.get(0));
					assertEquals(new PublishedIndex(fourthId, targetId, "customers_4"), indexes.get(1));
					return Future.succeededFuture();
				}))))
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
				IndexerRuntimeStatus.STARTED
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
				IndexerRuntimeStatus.STARTED
			).compose(firstId -> insertIndexer(
				repository,
				targetId,
				"customers_2",
				PublicationState.PUBLISHED,
				MutationState.READ_ONLY,
				IndexerRuntimeStatus.STARTED
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
		IndexerRuntimeStatus runtimeStatus
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

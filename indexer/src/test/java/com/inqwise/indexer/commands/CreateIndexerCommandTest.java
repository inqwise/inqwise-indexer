package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CreateIndexerCommandTest {
	@Test
	void createsIndexerWithRoleAndOwnership(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = new InMemoryCommandService()
			.register(new CreateIndexerCommandHandler(repository, eventBus));

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> commandService.submit(new CreateIndexerCommand(
				"load-writer",
				targetId,
				"customers",
				"customers--idx-load",
				"customers--queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(ignored -> repository.listIndexersByTargetId(targetId)))
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals(IndexerRole.LOAD_WRITER, indexers.get(0).role());
				assertEquals(IndexResourceOwnership.OWNER, indexers.get(0).indexOwnership());
				assertEquals(IndexerRuntimeState.ACTIVE, indexers.get(0).runtimeState());
				assertTrue(eventBus.events().stream()
					.anyMatch(event -> event.getIndexerId().equals(indexers.get(0).id())
						&& CreateIndexerCommand.TYPE.equals(event.getCommandType())));
				testContext.completeNow();
			})));
	}
}

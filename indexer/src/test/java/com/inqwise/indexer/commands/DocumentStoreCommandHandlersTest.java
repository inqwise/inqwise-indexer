package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.TestMetadataChangeNotifiers;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.operations.MetadataIndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class DocumentStoreCommandHandlersTest {
	@Test
	void createsStandardLifecycleAndProvisioningHandlers() {
		InMemoryCommandEngine commandService = new InMemoryCommandEngine();
		List<CommandHandler> handlers = DocumentStoreCommandHandlers.create(
			config(new InMemoryDocumentStoreMetadataRepository()),
			commandService
		);

		Set<String> types = handlers.stream()
			.map(CommandHandler::getType)
			.collect(Collectors.toSet());

		assertEquals(4, handlers.size());
		assertTrue(types.contains(DeleteIndexerCommand.TYPE));
		assertTrue(types.contains(CleanupDeletingIndexerCommand.TYPE));
		assertTrue(types.contains(CleanupResetIndexerQueueCommand.TYPE));
		assertTrue(types.contains(ResetIndexerQueueCommand.TYPE));
	}

	private DocumentStoreCommandHandlers.Config config(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		return new DocumentStoreCommandHandlers.Config(
			repository,
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP,
			TestMetadataChangeNotifiers.create(eventBus),
			new MetadataIndexerOperations(
				repository,
				TestMetadataChangeNotifiers.create(eventBus)
			)
		);
	}
}

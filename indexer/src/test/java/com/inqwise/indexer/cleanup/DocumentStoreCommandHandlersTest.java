package com.inqwise.indexer.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
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

		assertEquals(3, handlers.size());
		assertTrue(types.contains(DeleteIndexerCommand.TYPE));
		assertTrue(types.contains(CleanupDeletingIndexerCommand.TYPE));
		assertTrue(types.contains(CleanupResetIndexerQueueCommand.TYPE));
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

package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class DefaultIndexerNodeComponentsFactoryTest {
	@Test
	void createsCompleteDefaultComponentGraph(Vertx vertx) {
		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory()
			.create(vertx, new IndexerNodeOptions());

		assertAll(
			() -> assertNotNull(components.hotIndexActionsService()),
			() -> assertNotNull(components.runtime()),
			() -> assertNotNull(components.runtimeReconciler()),
			() -> assertNotNull(components.commandEngine()),
			() -> assertNotNull(components.indexerOperations()),
			() -> assertInstanceOf(
				InMemoryDocumentStoreMetadataRepository.class,
				components.repository()
			),
			() -> assertNotNull(components.lifecycleEventBus()),
			() -> assertInstanceOf(InMemoryIndexerQueue.class, components.queueResources()),
			() -> assertNotNull(components.targetDefinitionProvider()),
			() -> assertNotNull(components.indexerDefinitionProvider()),
			() -> assertInstanceOf(
				InMemoryIndexerDocumentStore.class,
				components.documentIndexResources()
			),
			() -> assertNotNull(components.invalidRouteCache()),
			() -> assertNotNull(components.invalidRouteMetadataChangeListener()),
			() -> assertNotNull(components.targetInvalidationRegistryBackend()),
			() -> assertNotNull(components.targetInvalidationRegistry()),
			() -> assertNotNull(components.targetInvalidationMetadataChangeListener()),
			() -> assertNotNull(components.targetInvalidationPoller())
		);
	}

	@Test
	void requiresFactoryInputs(Vertx vertx) {
		DefaultIndexerNodeComponentsFactory factory =
			new DefaultIndexerNodeComponentsFactory();

		assertThrows(NullPointerException.class, () -> factory.create(null, new IndexerNodeOptions()));
		assertThrows(NullPointerException.class, () -> factory.create(vertx, null));
	}

	@Test
	void createsDistinctBackendAndServiceProxy(Vertx vertx) {
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setTargetInvalidationOptions(new TargetInvalidationNodeOptions()
				.setProvider(TargetInvalidationNodeOptions.Provider.VERTX_SHARED_DATA));

		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory()
			.create(vertx, options);

		assertNotNull(components.targetInvalidationRegistryBackend());
		assertNotNull(components.targetInvalidationRegistry());
		assertNotSame(
			components.targetInvalidationRegistryBackend(),
			components.targetInvalidationRegistry()
		);
	}
}

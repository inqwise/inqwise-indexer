package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.providers.IndexerPlugins;
import com.inqwise.indexer.runtime.IndexerEventType;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultIndexerNodeComponentsFactoryTest {
	@Test
	void composesPluginsWithTheNodeOwnedDependencies(Vertx vertx) {
		AtomicReference<IndexerPluginContext> captured = new AtomicReference<>();
		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory()
			.create(
				vertx,
				new IndexerNodeOptions(),
				null,
				null,
				null,
				context -> {
					captured.set(context);
					return IndexerPlugins.empty();
				}
			);

		assertNotNull(captured.get());
		assertAll(
			() -> assertEquals(components.repository(), captured.get().repository()),
			() -> assertEquals(components.queueResources(), captured.get().queue()),
			() -> assertEquals(components.commandEngine(), captured.get().commandEngine()),
			() -> assertEquals(components.lifecycleEventBus(), captured.get().lifecycleEventBus())
		);
	}

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

	@Test
	void installsConfiguredTargetDefinitions(Vertx vertx) {
		IndexerNodeOptions options = IndexerNodeOptions.builder()
			.withTargetDefinitions(List.of(TargetDefinition.builder()
				.withTargetName("customers")
				.withPeriodStrategy(TargetPeriodStrategy.MONTHLY)
				.withAutoProvisionOnWrite(true)
				.build()))
			.build();

		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory()
			.create(vertx, options);
		var definition = components.targetDefinitionProvider()
			.getByName("customers")
			.toCompletionStage()
			.toCompletableFuture()
			.join();

		assertTrue(definition.isPresent());
		assertEquals(TargetPeriodStrategy.MONTHLY, definition.orElseThrow().periodStrategy());
		assertTrue(definition.orElseThrow().autoProvisionOnWrite());
	}

	@Test
	void wiresConfiguredRuntimeEventPublisher(
		Vertx vertx,
		VertxTestContext testContext
	) {
		AtomicInteger starts = new AtomicInteger();
		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory()
			.create(vertx, new IndexerNodeOptions(), event -> {
				if (event.getType() == IndexerEventType.INDEXER_STARTED) {
					starts.incrementAndGet();
				}
				return Future.succeededFuture();
			});
		Instant now = Instant.now();
		IndexerRecord record = IndexerRecord.builder()
			.withId(1)
			.withPrefix("idx")
			.withTargetId(1)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("queue-customers-1")
			.withType(IndexerType.INDEX)
			.withRole(IndexerRole.LIVE_WRITER)
			.withIndexOwnership(IndexResourceOwnership.OWNER)
			.withStatus(IndexerStatus.AVAILABLE)
			.withProvisioningState(IndexerProvisioningState.READY)
			.withRuntimeState(IndexerRuntimeState.ACTIVE)
			.withPublicationState(PublicationState.UNPUBLISHED)
			.withMutationState(MutationState.WRITABLE)
			.withCreatedAt(now)
			.withUpdatedAt(now)
			.withVersion(0)
			.build();

		components.runtime()
			.reconcile(record)
			.compose(ignored -> components.runtime().close(record.id()))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, starts.get());
				testContext.completeNow();
			})));
	}
}

package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueueConsumer;
import com.inqwise.indexer.IndexerQueueConsumerOptions;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.commands.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerPlugins;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadWriterActionReceiveCapabilityTest {
	@Test
	void coldSubmitPreparesLazyLoadWriterAndPublishesToLinkedLiveWriter(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		IndexerPlugins plugins = new IndexerPlugins(List.of(new LoadIndexerPlugin(metadata, loads)));
		RecordingEventBus eventBus = new RecordingEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commands = new InMemoryCommandService()
			.register(new SubmitIndexActionsCommandHandler(
				metadata,
				new StaticTargetDefinitionProvider(List.of()),
				eventBus,
				queue,
				null,
				plugins
			));

		insertLazyLoad(metadata, loads)
			.compose(load -> commands.submit(new SubmitIndexActionsCommand(List.of(
				PutDocumentActionItem.builder()
					.withTargetId(load.targetId())
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build()
			))).compose(ignored -> loads.getByIndexerId(load.indexerId()))
				.compose(updated -> metadata.getIndexerById(updated.orElseThrow().liveIndexerId())
					.map(liveWriter -> new Result(updated.orElseThrow(), liveWriter.orElseThrow()))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(1L, result.load().version());
				assertEquals(IndexerRole.LIVE_WRITER, result.liveWriter().role());
				assertEquals(IndexResourceOwnership.ATTACHED, result.liveWriter().indexOwnership());
				assertEquals("customers--idx-load", result.liveWriter().indexName());
				assertEquals("customers--queue-load--live", result.liveWriter().queueName());
				assertEquals(1, eventBus.events.size());
				assertEquals(result.liveWriter().id(), eventBus.events.get(0).getIndexerId());
				assertEquals(1, queue.publishedByQueueName.get(result.liveWriter().queueName()).size());
				assertConcretePut(
					queue.publishedByQueueName.get(result.liveWriter().queueName()).get(0),
					result.liveWriter()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void loadWriterReadinessIsNoAfterLiveWriterLinked(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		LoadWriterActionReceiveCapability capability =
			new LoadWriterActionReceiveCapability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.canReceive(
					loadWriter.orElseThrow(),
					PutDocumentActionItem.builder()
						.withTargetId(load.targetId())
						.withUid("42")
						.withDocument(new JsonObject().put("name", "Ada"))
						.build()
				).compose(readiness -> {
					assertEquals(ActionReceiveReadiness.REQUIRES_PREPARE, readiness);
					return capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
						"command-1",
						null,
						loadWriter.orElseThrow(),
						List.of(PutDocumentActionItem.builder()
							.withTargetId(load.targetId())
							.withUid("42")
							.withDocument(new JsonObject().put("name", "Ada"))
							.build()),
						null
					)).compose(ignored -> capability.canReceive(
						loadWriter.orElseThrow(),
						PutDocumentActionItem.builder()
							.withTargetId(load.targetId())
							.withUid("43")
							.withDocument(new JsonObject().put("name", "Grace"))
							.build()
					));
				})))
			.onComplete(testContext.succeeding(readiness -> testContext.verify(() -> {
				assertEquals(ActionReceiveReadiness.NO, readiness);
				testContext.completeNow();
			})));
	}

	private Future<IndexerLoadRecord> insertLazyLoad(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		return metadata.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> metadata.insertIndexer(new InsertIndexer(
				"load",
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
			)).compose(indexerId -> loads.insert(new InsertIndexerLoad(
				indexerId,
				targetId,
				null,
				LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION,
				"default",
				IndexerLoadState.HISTORICAL_LOADING,
				Instant.parse("2026-06-05T10:00:00Z"),
				null,
				null,
				null,
				null,
				null,
				false
			)).compose(ignored -> loads.getByIndexerId(indexerId))
				.map(found -> found.orElseThrow())));
	}

	private void assertConcretePut(IndexerActionItem item, IndexerRecord liveWriter) {
		PutDocumentActionItem put = (PutDocumentActionItem) item;
		assertEquals(liveWriter.targetId(), put.getTargetId());
		assertEquals(liveWriter.id(), put.getIndexerId());
		assertEquals(liveWriter.indexName(), put.getIndexName());
		assertEquals("42", put.getUid());
		assertEquals("Ada", put.getDocument().getString("name"));
	}

	private static class RecordingEventBus implements IndexerLifecycleEventBus {
		private final List<IndexerMetadataChanged> events = new ArrayList<>();

		@Override
		public Future<Void> publish(IndexerMetadataChanged event) {
			events.add(event);
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> publish(TargetMetadataChanged event) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> subscribe(Handler<IndexerMetadataChanged> handler) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> subscribeTarget(Handler<TargetMetadataChanged> handler) {
			return Future.succeededFuture();
		}
	}

	private static class RecordingQueue implements IndexerQueueClient {
		private final Map<String, List<IndexerActionItem>> publishedByQueueName =
			new LinkedHashMap<>();

		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.succeededFuture(new IndexerQueuePublisher() {
				@Override
				public Future<Void> publish(IndexerActionItem item) {
					publishedByQueueName
						.computeIfAbsent(queueName, ignored -> new ArrayList<>())
						.add(item);
					return Future.succeededFuture();
				}

				@Override
				public Future<Void> close() {
					return Future.succeededFuture();
				}
			});
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("consumer is not expected");
		}
	}

	private record Result(
		IndexerLoadRecord load,
		IndexerRecord liveWriter
	) {
	}
}

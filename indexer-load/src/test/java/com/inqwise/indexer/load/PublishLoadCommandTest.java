package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class PublishLoadCommandTest {
	@Test
	void publishesHistoricalOnlyLoadAsLiveWriter(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandService commands = commandService(metadata, loads);

		metadata.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> metadata.insertIndexer(new InsertIndexer(
				"load",
				targetId,
				"customers",
				"customers--idx-new",
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
			"default",
				IndexerLoadState.HISTORICAL_COMPLETE,
				Instant.parse("2026-06-05T10:00:00Z"),
				null,
				null,
				null,
				null,
				null,
				false
			)).compose(ignored -> commands.submit(new PublishLoadCommand(indexerId, 0L)))
				.compose(ignored -> metadata.getIndexerById(indexerId))
				.compose(found -> loads.getByIndexerId(indexerId)
					.map(load -> new Result(found.orElseThrow(), load.orElseThrow())))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(IndexerRole.LIVE_WRITER, result.indexer().role());
				assertEquals(IndexResourceOwnership.OWNER, result.indexer().indexOwnership());
				assertEquals(PublicationState.PUBLISHED, result.indexer().publicationState());
				assertEquals(IndexerLoadState.PUBLISHED, result.load().state());
				testContext.completeNow();
			})));
	}

	@Test
	void publishesLinkedLiveWriterAndTransfersOwnership(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandService commands = commandService(metadata, loads);

		metadata.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> metadata.insertIndexer(new InsertIndexer(
				"old",
				targetId,
				"customers",
				"customers--idx-old",
				"customers--queue-old",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(oldId -> metadata.insertIndexer(new InsertIndexer(
				"load",
				targetId,
				"customers",
				"customers--idx-new",
				"customers--queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(loadId -> metadata.insertIndexer(new InsertIndexer(
				"live",
				targetId,
				"customers",
				"customers--idx-new",
				"customers--queue-live",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.ATTACHED,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(liveId -> insertReadyLoad(loads, targetId, loadId, liveId)
				.compose(load -> commands.submit(new PublishLoadCommand(loadId, load.version())))
				.compose(ignored -> collect(metadata, loads, oldId, loadId, liveId))))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(PublicationState.RETIRED, result.oldPublished().publicationState());
				assertEquals(IndexResourceOwnership.ATTACHED, result.loadWriter().indexOwnership());
				assertEquals(PublicationState.PUBLISHED, result.liveWriter().publicationState());
				assertEquals(IndexResourceOwnership.OWNER, result.liveWriter().indexOwnership());
				assertEquals(IndexerLoadState.PUBLISHED, result.load().state());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanupAfterLinkedPublishDeletesOldAndLoadWriters(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandService commands = cleanupCommandService(metadata, loads);

		metadata.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> metadata.insertIndexer(new InsertIndexer(
				"old",
				targetId,
				"customers",
				"customers--idx-old",
				"customers--queue-old",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(oldId -> metadata.insertIndexer(new InsertIndexer(
				"load",
				targetId,
				"customers",
				"customers--idx-new",
				"customers--queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(loadId -> metadata.insertIndexer(new InsertIndexer(
				"live",
				targetId,
				"customers",
				"customers--idx-new",
				"customers--queue-live",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.ATTACHED,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(liveId -> insertReadyLoad(loads, targetId, loadId, liveId)
				.compose(load -> commands.submit(new PublishLoadCommand(loadId, load.version())))
				.compose(ignored -> collect(metadata, loads, oldId, loadId, liveId))))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(MutationState.DELETING, result.oldPublished().mutationState());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, result.oldPublished().runtimeState());
				assertEquals(MutationState.DELETING, result.loadWriter().mutationState());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, result.loadWriter().runtimeState());
				assertEquals(PublicationState.PUBLISHED, result.liveWriter().publicationState());
				assertEquals(IndexResourceOwnership.OWNER, result.liveWriter().indexOwnership());
				assertEquals(MutationState.WRITABLE, result.liveWriter().mutationState());
				assertEquals(IndexerLoadState.PUBLISHED, result.load().state());
				testContext.completeNow();
			})));
	}

	@Test
	void approvalPublishesReadyReviewedLoad(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandService commands = commandService(metadata, loads);
		commands.register(new ApproveLoadPublicationCommandHandler(
			loads,
			new InMemoryIndexerLifecycleEventBus(),
			commands
		));

		metadata.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> metadata.insertIndexer(new InsertIndexer(
				"load",
				targetId,
				"customers",
				"customers--idx-new",
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
			"default",
				IndexerLoadState.HISTORICAL_COMPLETE,
				Instant.parse("2026-06-05T10:00:00Z"),
				null,
				null,
				null,
				null,
				null,
				true
			)).compose(ignored -> commands.submit(new ApproveLoadPublicationCommand(
				indexerId,
				Instant.parse("2026-06-05T11:00:00Z"),
				"reviewer",
				"checked",
				0L
			))).compose(ignored -> metadata.getIndexerById(indexerId))
				.compose(found -> loads.getByIndexerId(indexerId)
					.map(load -> new Result(found.orElseThrow(), load.orElseThrow())))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(PublicationState.PUBLISHED, result.indexer().publicationState());
				assertEquals(IndexerLoadState.PUBLISHED, result.load().state());
				assertEquals("reviewer", result.load().approvedBy());
				assertEquals("checked", result.load().approvalReason());
				testContext.completeNow();
			})));
	}

	private Future<IndexerLoadRecord> insertReadyLoad(
		InMemoryIndexerLoadRepository loads,
		Integer targetId,
		Integer loadId,
		Integer liveId
	) {
		return loads.insert(new InsertIndexerLoad(
			loadId,
			targetId,
			liveId,
			"default",
			IndexerLoadState.HISTORICAL_COMPLETE,
			Instant.parse("2026-06-05T10:00:00Z"),
			Instant.parse("2026-06-05T09:55:00Z"),
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> loads.markBarrierReached(new UpdateIndexerLoadBarrier(
			loadId,
			"barrier-1",
			Instant.parse("2026-06-05T10:30:00Z"),
			Instant.parse("2026-06-05T10:31:00Z"),
			0L
		))).compose(ignored -> loads.getByIndexerId(loadId).map(found -> found.orElseThrow()));
	}

	private Future<LinkedResult> collect(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		Integer oldId,
		Integer loadId,
		Integer liveId
	) {
		return metadata.getIndexerById(oldId)
			.compose(old -> metadata.getIndexerById(loadId)
				.compose(loadWriter -> metadata.getIndexerById(liveId)
					.compose(liveWriter -> loads.getByIndexerId(loadId)
						.map(load -> new LinkedResult(
							old.orElseThrow(),
							loadWriter.orElseThrow(),
							liveWriter.orElseThrow(),
							load.orElseThrow()
						)))));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		return new InMemoryCommandService()
			.register(new PublishLoadCommandHandler(
				metadata,
				loads,
				new InMemoryIndexerLifecycleEventBus()
			));
	}

	private InMemoryCommandService cleanupCommandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commands = new InMemoryCommandService();
		commands
			.register(new DeleteIndexerCommandHandler(metadata, eventBus))
			.register(new CleanupPublishedLoadCommandHandler(metadata, loads, commands))
			.register(new PublishLoadCommandHandler(metadata, loads, eventBus, commands));
		return commands;
	}

	private record Result(
		com.inqwise.indexer.metadata.IndexerRecord indexer,
		IndexerLoadRecord load
	) {
	}

	private record LinkedResult(
		com.inqwise.indexer.metadata.IndexerRecord oldPublished,
		com.inqwise.indexer.metadata.IndexerRecord loadWriter,
		com.inqwise.indexer.metadata.IndexerRecord liveWriter,
		IndexerLoadRecord load
	) {
	}
}

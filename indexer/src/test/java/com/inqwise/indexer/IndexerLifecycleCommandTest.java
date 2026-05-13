package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.commands.ActivateIndexerCommand;
import com.inqwise.indexer.commands.ActivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeactivateIndexerCommand;
import com.inqwise.indexer.commands.DeactivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeleteIndexerCommand;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerLifecycleCommandTest {
	@Test
	void activateCommandUpdatesRepositoryAndFansOutToAllNodeSubscribers(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> nodeA = new ArrayList<>();
		List<IndexerLifecycleChanged> nodeB = new ArrayList<>();
		IndexerModel model = inactiveModel();

		repository.save(model)
			.compose(id -> eventBus.subscribe(nodeA::add)
				.compose(ignored -> eventBus.subscribe(nodeB::add))
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id)))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerModel updated = found.orElseThrow();
				assertEquals(IndexerStatus.STARTED, updated.getStatus());
				assertEquals(1L, updated.getVersion());
				assertEquals(1, nodeA.size());
				assertEquals(1, nodeB.size());
				assertEquals(updated.getId(), nodeA.get(0).getIndexerId());
				assertEquals(ActivateIndexerCommand.TYPE, nodeA.get(0).getCommandType());
				assertEquals(ActivateIndexerCommand.TYPE, nodeB.get(0).getCommandType());
				assertEquals(1L, nodeA.get(0).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void lifecycleBusReplaysPublishedEventsToLateSubscribers(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> lateNode = new ArrayList<>();

		repository.save(inactiveModel())
			.compose(id -> commandService.submit(new ActivateIndexerCommand(id)))
			.compose(ignored -> eventBus.subscribe(lateNode::add))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, lateNode.size());
				assertEquals(ActivateIndexerCommand.TYPE, lateNode.get(0).getCommandType());
				assertEquals(1L, lateNode.get(0).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void repeatedLifecycleCommandsAreIdempotentAndKeepVersionStable(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		repository.save(inactiveModel())
			.compose(id -> eventBus.subscribe(events::add)
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id)))
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id)))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id)))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id)))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerModel updated = found.orElseThrow();
				assertEquals(IndexerStatus.NON_ACTIVE, updated.getStatus());
				assertEquals(2L, updated.getVersion());
				assertEquals(4, events.size());
				assertEquals(1L, events.get(0).getVersion());
				assertEquals(1L, events.get(1).getVersion());
				assertEquals(2L, events.get(2).getVersion());
				assertEquals(2L, events.get(3).getVersion());
				assertEquals(ActivateIndexerCommand.TYPE, events.get(0).getCommandType());
				assertEquals(ActivateIndexerCommand.TYPE, events.get(1).getCommandType());
				assertEquals(DeactivateIndexerCommand.TYPE, events.get(2).getCommandType());
				assertEquals(DeactivateIndexerCommand.TYPE, events.get(3).getCommandType());
				testContext.completeNow();
			})));
	}

	@Test
	void deleteCommandMarksIndexerDeletedAndFansOutLifecycleEvent(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		repository.save(inactiveModel())
			.compose(id -> eventBus.subscribe(events::add)
				.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id)))
				.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id)))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerModel updated = found.orElseThrow();
				assertEquals(IndexerStatus.DELETED, updated.getStatus());
				assertEquals(1L, updated.getVersion());
				assertEquals(2, events.size());
				assertEquals(DeleteIndexerCommand.TYPE, events.get(0).getCommandType());
				assertEquals(DeleteIndexerCommand.TYPE, events.get(1).getCommandType());
				assertEquals(1L, events.get(0).getVersion());
				assertEquals(1L, events.get(1).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void deleteCommandMissingIndexerIsCleanupIdempotent(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> commandService.submit(new DeleteIndexerCommand(404)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(0, events.size());
				testContext.completeNow();
			})));
	}

	@Test
	void activateAfterDeleteFailsAndLifecycleDeleteRemainsIdempotent(
		VertxTestContext testContext
	) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		repository.save(inactiveModel())
			.compose(id -> eventBus.subscribe(events::add)
				.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id)))
					.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id)))
					.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id)))
					.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id))
						.map(activated -> false)
						.recover(error -> Future.succeededFuture(true)))
				.compose(activateFailed -> repository.get(id)
					.map(found -> new ActivateAfterDeleteResult(activateFailed, found))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				IndexerModel updated = result.found().orElseThrow();

				assertTrue(result.activateFailed());
				assertEquals(IndexerStatus.DELETED, updated.getStatus());
				assertEquals(1L, updated.getVersion());
				assertEquals(3, events.size());
				assertEquals(DeleteIndexerCommand.TYPE, events.get(0).getCommandType());
				assertEquals(DeactivateIndexerCommand.TYPE, events.get(1).getCommandType());
				assertEquals(DeleteIndexerCommand.TYPE, events.get(2).getCommandType());
				testContext.completeNow();
			})));
	}

	private record ActivateAfterDeleteResult(
		boolean activateFailed,
		Optional<IndexerModel> found
	) {
	}

	private InMemoryCommandService commandService(
		InMemoryIndexerRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandService()
			.register(new ActivateIndexerCommandHandler(repository, eventBus))
			.register(new DeactivateIndexerCommandHandler(repository, eventBus))
			.register(new DeleteIndexerCommandHandler(repository, eventBus));
	}

	private IndexerModel inactiveModel() {
		return IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withStatus(IndexerStatus.NON_ACTIVE)
			.build();
	}
}

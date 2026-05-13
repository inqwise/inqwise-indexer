package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import com.inqwise.indexer.commands.ActivateIndexerCommand;
import com.inqwise.indexer.commands.ActivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeactivateIndexerCommand;
import com.inqwise.indexer.commands.DeactivateIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id, "activate-1")))
				.compose(ignored -> repository.get(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerModel updated = found.orElseThrow();
				assertEquals(IndexerStatus.STARTED, updated.getStatus());
				assertEquals(1L, updated.getVersion());
				assertEquals(1, nodeA.size());
				assertEquals(1, nodeB.size());
				assertEquals(IndexerStatus.STARTED, nodeA.get(0).getStatus());
				assertEquals(IndexerStatus.STARTED, nodeB.get(0).getStatus());
				assertEquals(1L, nodeA.get(0).getVersion());
				assertEquals("activate-1", nodeA.get(0).getCommandId());
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
			.compose(id -> commandService.submit(new ActivateIndexerCommand(id, "activate-1")))
			.compose(ignored -> eventBus.subscribe(lateNode::add))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, lateNode.size());
				assertEquals(IndexerStatus.STARTED, lateNode.get(0).getStatus());
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
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id, "activate-1")))
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id, "activate-2")))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id, "deactivate-1")))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id, "deactivate-2")))
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
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryIndexerRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandService()
			.register(new ActivateIndexerCommandHandler(repository, eventBus))
			.register(new DeactivateIndexerCommandHandler(repository, eventBus));
	}

	private IndexerModel inactiveModel() {
		return IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withStatus(IndexerStatus.NON_ACTIVE)
			.build();
	}
}

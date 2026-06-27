package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;

import io.vertx.core.Future;

public class ApproveLoadPublicationCommandHandler implements CommandHandler {
	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final LoadPublicationOrchestrator publicationOrchestrator;

	public ApproveLoadPublicationCommandHandler(
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.publicationOrchestrator = new LoadPublicationOrchestrator(
			Objects.requireNonNull(commandService, "commandService")
		);
	}

	@Override
	public String getType() {
		return ApproveLoadPublicationCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		ApproveLoadPublicationCommand approve = new ApproveLoadPublicationCommand(command.toJson());

		return loadRepository.getByIndexerId(approve.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + approve.getIndexerId())))
			.compose(load -> approve(load, approve));
	}

	private Future<Void> approve(
		IndexerLoadRecord load,
		ApproveLoadPublicationCommand approve
	) {
		if (load.version() != approve.getExpectedLoadVersion()) {
			return Future.failedFuture(
				"Indexer load version conflict for id " + load.indexerId() + ": expected "
					+ approve.getExpectedLoadVersion() + " but was " + load.version()
			);
		}

		if (load.state() == IndexerLoadState.PUBLISHED) {
			return Future.succeededFuture();
		}
		if (load.state() == IndexerLoadState.FAILED || load.state() == IndexerLoadState.CANCELLED) {
			return Future.failedFuture("Indexer load is not approvable: " + load.state());
		}
		if (!load.reviewRequired() || load.state() != IndexerLoadState.WAITING_FOR_REVIEW) {
			return Future.failedFuture("Indexer load is not waiting for review: " + load.state());
		}

		return loadRepository.approve(new UpdateIndexerLoadApproval(
			load.indexerId(),
			approve.getApprovedAt(),
			approve.getApprovedBy(),
			approve.getApprovalReason(),
			load.version()
		)).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())))
			.compose(updated -> {
				eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
					updated.indexerId(),
					updated.targetId(),
					getType(),
					updated.version()
				));
				return publicationOrchestrator.publishIfReady(updated);
			});
	}
}

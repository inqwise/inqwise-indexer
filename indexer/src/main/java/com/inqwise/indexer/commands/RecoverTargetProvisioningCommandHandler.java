package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;

import io.vertx.core.Future;

public class RecoverTargetProvisioningCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus eventBus;

	public RecoverTargetProvisioningCommandHandler(
		DocumentStoreMetadataRepository repository
	) {
		this(repository, IndexerLifecycleEventBus.NOOP);
	}

	public RecoverTargetProvisioningCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
	}

	@Override
	public String getType() {
		return RecoverTargetProvisioningCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		RecoverTargetProvisioningCommand recover =
			new RecoverTargetProvisioningCommand(command.toJson());

		return repository.getTargetById(recover.getTargetId())
			.compose(found -> found
				.map(target -> recover(target, recover))
				.orElseGet(() -> Future.failedFuture(
					"Target not found: " + recover.getTargetId()
				)));
	}

	private Future<Void> recover(
		TargetRecord target,
		RecoverTargetProvisioningCommand recover
	) {
		if (target.status() != TargetStatus.ACTIVE) {
			return Future.failedFuture(
				"Target is not active: " + recover.getTargetId()
			);
		}

		if (target.provisioningState() != TargetProvisioningState.FAILED) {
			return Future.failedFuture(
				"Target provisioning is not failed: " + recover.getTargetId()
			);
		}

		return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
			target.id(),
			TargetProvisioningState.READY,
			recover.getExpectedVersion()
		)).compose(ignored -> repository.getTargetById(target.id()))
			.compose(found -> found
				.map(current -> eventBus.publish(new TargetMetadataChanged(
					current.id(),
					current.targetName(),
					current.periodKey(),
					RecoverTargetProvisioningCommand.TYPE,
					current.version()
				)))
				.orElseGet(() -> Future.succeededFuture()));
	}
}

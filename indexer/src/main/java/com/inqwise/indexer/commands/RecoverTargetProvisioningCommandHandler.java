package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;

import io.vertx.core.Future;

public class RecoverTargetProvisioningCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;
	private final MetadataChangeNotifier metadataChangeNotifier;

	public RecoverTargetProvisioningCommandHandler(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
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
		if (alreadyApplied(target, recover)) {
			return publish(target, target.version());
		}

		if (target.version() != recover.getExpectedVersion()) {
			return Future.failedFuture(
				"Target version conflict for id " + target.id() + ": expected "
					+ recover.getExpectedVersion() + " but was " + target.version()
			);
		}

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
		)).compose(ignored -> publish(target, recover.getExpectedVersion() + 1L));
	}

	private boolean alreadyApplied(
		TargetRecord target,
		RecoverTargetProvisioningCommand recover
	) {
		return recover.getExpectedVersion() >= 0L
			&& recover.getExpectedVersion() < Long.MAX_VALUE
			&& target.version() == recover.getExpectedVersion() + 1L
			&& target.status() == TargetStatus.ACTIVE
			&& target.provisioningState() == TargetProvisioningState.READY;
	}

	private Future<Void> publish(TargetRecord target, long version) {
		return metadataChangeNotifier.targetChanged(new TargetMetadataChanged(
			target.id(),
			target.targetName(),
			target.periodKey(),
			RecoverTargetProvisioningCommand.TYPE,
			version
		));
	}
}

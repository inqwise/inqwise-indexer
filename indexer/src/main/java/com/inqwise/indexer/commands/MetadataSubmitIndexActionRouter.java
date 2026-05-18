package com.inqwise.indexer.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.RemoveDocumentActionItem;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetDefinitionRecord;
import com.inqwise.indexer.metadata.TargetNameValidator;
import com.inqwise.indexer.metadata.TargetPeriod;
import com.inqwise.indexer.metadata.TargetPeriodResolver;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;

import io.vertx.core.Future;

class MetadataSubmitIndexActionRouter {
	private final DocumentStoreMetadataRepository repository;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();

	MetadataSubmitIndexActionRouter(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	Future<List<RoutedIndexActions>> route(SubmitIndexActionsCommand submit) {
		if (submit.getActions().isEmpty()) {
			return Future.failedFuture("No actions submitted for command " + submit.getCommandId());
		}

		Future<Map<IndexerRecord, List<IndexerActionItem>>> actionsByIndexer =
			Future.succeededFuture(new LinkedHashMap<>());

		for (IndexerActionItem action : submit.getActions()) {
			ActionDestination destination = ActionDestination.from(action);
			if (destination.isEmpty() && !hasPublicTarget(submit)) {
				return Future.failedFuture("Action destination is missing for command " + submit.getCommandId());
			}

			actionsByIndexer = actionsByIndexer.compose(groups ->
				resolveIndexers(submit, destination)
					.compose(indexers -> {
						for (IndexerRecord indexer : indexers) {
							groups.computeIfAbsent(indexer, ignored -> new ArrayList<>())
								.add(toConcreteAction(action, indexer));
						}

						return Future.succeededFuture(groups);
					}));
		}

		return actionsByIndexer.map(groups -> groups.entrySet().stream()
			.map(entry -> new RoutedIndexActions(
				entry.getKey().id(),
				entry.getKey().version(),
				getQueueName(entry.getKey()),
				entry.getValue()
			))
			.toList());
	}

	private Future<List<IndexerRecord>> resolveIndexers(
		SubmitIndexActionsCommand submit,
		ActionDestination destination
	) {
		if (destination.indexerId() != null) {
			return repository.getIndexerById(destination.indexerId())
				.compose(found -> found
					.map(indexer -> verifyIndexer(destination, indexer)
						.map(List::of))
					.orElseGet(() -> Future.failedFuture(
						"Indexer not found: " + destination.indexerId()
					)));
		}

		if (destination.targetId() == null && hasPublicTarget(submit)) {
			return resolveConcreteTarget(submit)
				.compose(target -> resolveIndexersByTarget(submit, destination, target, true));
		}

		if (destination.targetId() == null) {
			return Future.failedFuture(
				"Action target id is required for metadata routing in command " + submit.getCommandId()
			);
		}

		return repository.getTargetById(destination.targetId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Target not found: " + destination.targetId()
				)))
			.compose(target -> resolveIndexersByTarget(submit, destination, target, false));
	}

	private Future<List<IndexerRecord>> resolveIndexersByTarget(
		SubmitIndexActionsCommand submit,
		ActionDestination destination,
		TargetRecord target,
		boolean autoProvision
	) {
		if (target.status() != TargetStatus.ACTIVE) {
			return Future.failedFuture("Target is not active: " + target.id());
		}

		if (target.provisioningState() == TargetProvisioningState.PROVISIONING) {
			return Future.failedFuture("Target provisioning is in progress: " + target.id());
		}

		if (target.provisioningState() == TargetProvisioningState.FAILED) {
			return Future.failedFuture("Target provisioning failed: " + target.id());
		}

		return repository.listWritableIndexersByTargetId(target.id())
			.compose(indexers -> {
				List<IndexerRecord> matches = indexers.stream()
					.filter(indexer -> indexer.runtimeStatus() == IndexerRuntimeStatus.STARTED
						|| indexer.runtimeStatus() == IndexerRuntimeStatus.COMPLETED)
					.filter(indexer -> destination.indexName() == null
						|| destination.indexName().equals(indexer.indexName()))
					.toList();

				if (matches.isEmpty()) {
					return autoProvision
						? ensureWritableIndexer(target).map(List::of)
						: Future.failedFuture("No writable indexers found for target id: " + target.id());
				}

				return Future.succeededFuture(matches);
			});
	}

	private Future<TargetRecord> resolveConcreteTarget(SubmitIndexActionsCommand submit) {
		return resolveTargetDefinition(submit)
			.compose(targetDefinition -> {
				if (targetDefinition.status() != TargetStatus.ACTIVE) {
					return Future.failedFuture("Target definition is not active: " + targetDefinition.id());
				}

				if (targetDefinition.periodStrategy() != TargetPeriodStrategy.NONE
					&& submit.getTimestamp() == null) {
					return Future.failedFuture(
						"Timestamp is required for target period strategy: "
							+ targetDefinition.periodStrategy()
					);
				}

				TargetPeriod period;
				try {
					period = periodResolver.resolve(
						targetDefinition.periodStrategy(),
						submit.getTimestamp()
					);
				} catch (RuntimeException error) {
					return Future.failedFuture(error);
				}

				return repository.ensureTarget(targetDefinition, period);
			});
	}

	private Future<TargetDefinitionRecord> resolveTargetDefinition(SubmitIndexActionsCommand submit) {
		Future<TargetDefinitionRecord> resolved = null;

		if (submit.getTargetUid() != null) {
			resolved = repository.getTargetDefinitionByUid(submit.getTargetUid())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture(
						"Target definition not found by uid: " + submit.getTargetUid()
					)));
		}

		if (submit.getTargetName() != null) {
			Future<TargetDefinitionRecord> byName = repository.getTargetDefinitionByName(submit.getTargetName())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture(
						"Target definition not found by name: " + submit.getTargetName()
					)));

			if (resolved == null) {
				resolved = byName;
			} else {
				resolved = resolved.compose(byUid -> byName.compose(byTargetName -> {
					if (!byUid.id().equals(byTargetName.id())) {
						return Future.failedFuture("Target uid and name resolve to different targets");
					}

					return Future.succeededFuture(byUid);
				}));
			}
		}

		return resolved == null
			? Future.failedFuture("Target reference is missing for command " + submit.getCommandId())
			: resolved;
	}

	private Future<IndexerRecord> ensureWritableIndexer(TargetRecord target) {
		String suffix = UUID.randomUUID().toString();
		String indexName = target.targetName() + "--idx-" + suffix;
		String queueName = target.targetName() + "--queue-" + suffix;
		TargetNameValidator.requireGeneratedResourceName(indexName);
		TargetNameValidator.requireGeneratedResourceName(queueName);
		return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
			target.id(),
			TargetProvisioningState.PROVISIONING,
			target.version()
		)).compose(ignored -> repository.insertIndexer(new InsertIndexer(
				null,
				target.id(),
				target.targetName(),
				indexName,
				queueName,
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(repository::getIndexerById)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Created indexer not found for target: " + target.id())))
			.compose(indexer -> repository.getTargetById(target.id())
				.compose(found -> found
					.map(current -> repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
						target.id(),
						TargetProvisioningState.READY,
						current.version()
					)).map(indexer))
					.orElseGet(() -> Future.failedFuture("Target not found: " + target.id()))))
			.recover(error -> recoverWritableIndexerProvisioning(target, error));
	}

	private Future<IndexerRecord> recoverWritableIndexerProvisioning(
		TargetRecord target,
		Throwable error
	) {
		return repository.getTargetById(target.id())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture(error);
				}

				TargetRecord current = found.get();
				if (current.provisioningState() == TargetProvisioningState.PROVISIONING
					&& current.version() != target.version()) {
					return Future.failedFuture("Target provisioning is in progress: " + target.id());
				}

				return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
					target.id(),
					TargetProvisioningState.FAILED,
					current.version()
				)).compose(ignored -> Future.failedFuture(error));
			});
	}

	private Future<IndexerRecord> verifyIndexer(
		ActionDestination destination,
		IndexerRecord indexer
	) {
		if (indexer.runtimeStatus() != IndexerRuntimeStatus.STARTED
			&& indexer.runtimeStatus() != IndexerRuntimeStatus.COMPLETED) {
			return Future.failedFuture("Indexer is not active: " + indexer.indexName());
		}

		if (indexer.mutationState() != MutationState.WRITABLE) {
			return Future.failedFuture("Indexer is not writable: " + indexer.indexName());
		}

		if (destination.targetId() != null && !destination.targetId().equals(indexer.targetId())) {
			return Future.failedFuture("Indexer target id mismatch: " + indexer.indexName());
		}

		if (destination.indexName() != null && !destination.indexName().equals(indexer.indexName())) {
			return Future.failedFuture("Indexer index mismatch: " + indexer.indexName());
		}

		return Future.succeededFuture(indexer);
	}

	private IndexerActionItem toConcreteAction(
		IndexerActionItem action,
		IndexerRecord indexer
	) {
		return switch (action.getActionType()) {
			case PUT_DOCUMENT -> {
				PutDocumentActionItem put = (PutDocumentActionItem) action;
				yield PutDocumentActionItem.builder()
					.withTargetId(indexer.targetId())
					.withIndexerId(indexer.id())
					.withIndexName(indexer.indexName())
					.withUid(put.getUid())
					.withSequence(put.getSequence())
					.withMutationId(put.getMutationId())
					.withDocument(put.getDocument())
					.build();
			}
			case REMOVE_DOCUMENT -> {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) action;
				yield RemoveDocumentActionItem.builder()
					.withTargetId(indexer.targetId())
					.withIndexerId(indexer.id())
					.withTargetName(remove.getTargetName())
					.withIndexName(indexer.indexName())
					.withUid(remove.getUid())
					.withSequence(remove.getSequence())
					.withMutationId(remove.getMutationId())
					.build();
			}
			case COMPLETE -> CompleteIndexActionItem.builder()
				.withTargetId(indexer.targetId())
				.withIndexerId(indexer.id())
				.build();
		};
	}

	private String getQueueName(IndexerRecord indexer) {
		return indexer.queueName() == null ? indexer.indexName() : indexer.queueName();
	}

	private boolean hasPublicTarget(SubmitIndexActionsCommand submit) {
		return submit.getTargetUid() != null || submit.getTargetName() != null;
	}
}

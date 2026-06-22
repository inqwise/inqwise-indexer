package com.inqwise.indexer.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.inqwise.indexer.CatchUpBarrierActionItem;
import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.Actions;
import com.inqwise.indexer.actions.IndexerActionRouteContext;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetPeriod;
import com.inqwise.indexer.metadata.TargetPeriodResolver;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.PrepareIndexerForActionsRequest;
import com.inqwise.indexer.providers.PreparedIndexers;
import com.inqwise.indexer.provisioning.CreateIndexerProvisioningRequest;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;

import io.vertx.core.Future;

class MetadataSubmitIndexActionRouter {
	private final DocumentStoreMetadataRepository repository;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final List<IndexerActionReceiveCapability> receiveCapabilities;
	private final IndexerProvisioningService provisioningService;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();

	MetadataSubmitIndexActionRouter(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		List<IndexerActionReceiveCapability> receiveCapabilities
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.receiveCapabilities = List.copyOf(Objects.requireNonNull(
			receiveCapabilities,
			"receiveCapabilities"
		));
		this.provisioningService = new IndexerProvisioningService(
			repository,
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources
		);
	}

	Future<List<RoutedIndexActions>> route(SubmitIndexActionsCommand submit) {
		if (submit.getActions().isEmpty()) {
			return Future.failedFuture("No actions submitted for command " + submit.getCorrelationId());
		}

		MetadataRoutingContext routingContext = new MetadataRoutingContext();
		Future<Map<IndexerRecord, List<IndexerActionItem>>> actionsByIndexer =
			Future.succeededFuture(new LinkedHashMap<>());

		for (IndexerActionItem action : submit.getActions()) {
			ActionDestination destination = ActionDestination.from(action);
			if (destination.isEmpty() && !hasPublicTarget(submit)) {
				return Future.failedFuture("Action destination is missing for command " + submit.getCorrelationId());
			}

			actionsByIndexer = actionsByIndexer.compose(groups ->
				resolveIndexers(submit, action, destination, routingContext)
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
				entry.getValue(),
				routingContext.metadataChanged(entry.getKey().id())
			))
			.toList());
	}

	private Future<List<IndexerRecord>> resolveIndexers(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		ActionDestination destination,
		MetadataRoutingContext routingContext
	) {
		if (destination.indexerId() != null) {
			return repository.getIndexerById(destination.indexerId())
				.compose(found -> found
					.map(indexer -> verifyIndexer(destination, indexer)
						.map(List::of))
					.orElseGet(() -> Future.failedFuture(
						CommandFailure.stableInvalid("Indexer not found: " + destination.indexerId())
					)));
		}

		if (destination.targetId() == null && hasPublicTarget(submit)) {
			return resolveConcreteTarget(submit)
				.compose(target -> resolveIndexersByTarget(
					submit,
					action,
					destination,
					target.record(),
					target.autoProvisionOnWrite(),
					routingContext
				));
		}

		if (destination.targetId() == null) {
			return Future.failedFuture(
				"Action target id is required for metadata routing in command " + submit.getCorrelationId()
			);
		}

		return repository.getTargetById(destination.targetId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					CommandFailure.stableInvalid("Target not found: " + destination.targetId())
				)))
			.compose(target -> resolveIndexersByTarget(
				submit,
				action,
				destination,
				target,
				false,
				routingContext
			));
	}

	private Future<List<IndexerRecord>> resolveIndexersByTarget(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		ActionDestination destination,
		TargetRecord target,
		boolean autoProvision,
		MetadataRoutingContext routingContext
	) {
		if (target.status() != TargetStatus.ACTIVE) {
			return Future.failedFuture("Target is not active: " + target.id());
		}

		if (target.provisioningState() == TargetProvisioningState.PROVISIONING) {
			return Future.failedFuture(CommandFailure.retryable(
				"Target provisioning is in progress: " + target.id()
			));
		}

		if (target.provisioningState() == TargetProvisioningState.FAILED) {
			return Future.failedFuture(CommandFailure.finalFailure(
				"Target provisioning failed: " + target.id()
			));
		}

		return repository.listWritableIndexersByTargetId(target.id())
			.compose(indexers -> {
				List<IndexerRecord> activeCandidates = indexers.stream()
					.filter(indexer -> indexer.runtimeState() == IndexerRuntimeState.ACTIVE)
					.filter(indexer -> destination.indexName() == null
						|| destination.indexName().equals(indexer.indexName()))
					.toList();

				List<IndexerRecord> matches = activeCandidates.stream()
					.filter(indexer -> indexer.role() == IndexerRole.LIVE_WRITER)
					.toList();

				if (matches.isEmpty()) {
					return prepareReceivers(submit, action, target, activeCandidates, routingContext)
						.compose(prepared -> {
							if (!prepared.isEmpty()) {
								return Future.succeededFuture(prepared);
							}

							return autoProvision
								? ensureWritableIndexer(target, routingContext).map(List::of)
								: Future.failedFuture(CommandFailure.stableInvalid(
									"No writable indexers found for target id: " + target.id()
								));
						});
				}

				return Future.succeededFuture(matches);
			});
	}

	private Future<List<IndexerRecord>> prepareReceivers(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		TargetRecord target,
		List<IndexerRecord> candidates,
		MetadataRoutingContext routingContext
	) {
		Future<List<IndexerRecord>> prepared = Future.succeededFuture(List.of());

		for (IndexerRecord candidate : candidates) {
			prepared = prepared.compose(current -> {
				if (!current.isEmpty()) {
					return Future.succeededFuture(current);
				}

				return prepareReceiver(submit, action, target, candidate, routingContext);
			});
		}

		return prepared;
	}

	private Future<List<IndexerRecord>> prepareReceiver(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		TargetRecord target,
		IndexerRecord candidate,
		MetadataRoutingContext routingContext
	) {
		Future<List<IndexerRecord>> prepared = Future.succeededFuture(List.of());

		for (IndexerActionReceiveCapability capability : receiveCapabilities) {
			prepared = prepared.compose(current -> {
				if (!current.isEmpty()) {
					return Future.succeededFuture(current);
				}

				return capability.canReceive(candidate, action)
					.compose(readiness -> {
						if (readiness == ActionReceiveReadiness.NO) {
							return Future.succeededFuture(List.of());
						}
						if (readiness == ActionReceiveReadiness.YES) {
							return Future.succeededFuture(List.of(candidate));
						}

						return capability.prepareToReceive(new PrepareIndexerForActionsRequest(
							submit.getCorrelationId(),
							target,
							candidate,
							List.of(action),
							submit.getTimestamp()
						)).map(result -> preparedIndexers(result, routingContext));
					});
			});
		}

		return prepared;
	}

	private List<IndexerRecord> preparedIndexers(
		PreparedIndexers prepared,
		MetadataRoutingContext routingContext
	) {
		if (prepared.metadataChanged()) {
			for (IndexerRecord indexer : prepared.indexers()) {
				routingContext.markMetadataChanged(indexer.id());
			}
		}

		return prepared.indexers();
	}

	private Future<ResolvedTarget> resolveConcreteTarget(SubmitIndexActionsCommand submit) {
		return resolveTargetDefinition(submit)
			.compose(targetDefinition -> {
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

				return repository.getTargetByDefinitionAndPeriod(new ConcreteTargetKey(
					targetDefinition.targetName(),
					period.key()
				)).compose(found -> found
					.map(target -> Future.succeededFuture(new ResolvedTarget(
						target,
						targetDefinition.autoProvisionOnWrite()
					)))
					.orElseGet(() -> {
						if (!targetDefinition.autoProvisionOnWrite()) {
							return Future.failedFuture(CommandFailure.stableInvalid(
								"Auto provisioning is disabled for target: "
									+ targetDefinition.targetName()
							));
						}

						return repository.ensureTarget(targetDefinition.targetName(), period)
							.map(target -> new ResolvedTarget(target, true));
					}));
			});
	}

	private Future<TargetDefinition> resolveTargetDefinition(SubmitIndexActionsCommand submit) {
		return submit.getTargetName() == null
			? Future.failedFuture(CommandFailure.stableInvalid(
				"Target reference is missing for command " + submit.getCorrelationId()
			))
			: targetDefinitionProvider.getByName(submit.getTargetName())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture(
						CommandFailure.stableInvalid(
							"Target definition not found by name: " + submit.getTargetName()
						)
					)));
	}

	private Future<IndexerRecord> ensureWritableIndexer(
		TargetRecord target,
		MetadataRoutingContext routingContext
	) {
		GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(target.targetName());
		return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
			target.id(),
			TargetProvisioningState.PROVISIONING,
			target.version()
		)).recover(error -> Future.failedFuture(CommandFailure.retryable(
			"Target provisioning lock changed: " + target.id(),
			error
		))).compose(ignored -> provisioningService.createIndexer(new CreateIndexerProvisioningRequest(
				resources.prefix(),
				target.id(),
				target.targetName(),
				resources.indexName(),
				resources.queueName(),
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.onSuccess(indexer -> routingContext.markMetadataChanged(indexer.id()))
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
				if (current.provisioningState() == TargetProvisioningState.FAILED) {
					return Future.failedFuture(CommandFailure.finalFailure(
						"Target provisioning failed: " + target.id(),
						error
					));
				}

				if (current.version() != target.version() + 1
					|| current.provisioningState() != TargetProvisioningState.PROVISIONING) {
					return Future.failedFuture(CommandFailure.retryable(
						"Target provisioning changed: " + target.id(),
						error
					));
				}

				return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
					target.id(),
					TargetProvisioningState.FAILED,
					current.version()
				)).compose(ignored -> Future.failedFuture(CommandFailure.retryable(
					"Writable indexer provisioning failed: " + target.id(),
					error
				)));
			});
	}

	private Future<IndexerRecord> verifyIndexer(
		ActionDestination destination,
		IndexerRecord indexer
	) {
		if (indexer.status() != IndexerStatus.AVAILABLE
			|| indexer.provisioningState() != IndexerProvisioningState.READY
			|| indexer.runtimeState() != IndexerRuntimeState.ACTIVE) {
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
				yield routeAction(action, indexer);
			}
			case REMOVE_DOCUMENT -> routeAction(action, indexer);
			case COMPLETE -> CompleteIndexActionItem.builder()
				.withTargetId(indexer.targetId())
				.withIndexerId(indexer.id())
				.build();
			case CATCH_UP_BARRIER -> {
				CatchUpBarrierActionItem barrier = (CatchUpBarrierActionItem) action;
				yield CatchUpBarrierActionItem.builder()
					.withTargetId(indexer.targetId())
					.withIndexerId(indexer.id())
					.withBarrierId(barrier.getBarrierId())
					.withBarrierTimestamp(barrier.getBarrierTimestamp())
					.build();
			}
		};
	}

	private IndexerActionItem routeAction(
		IndexerActionItem action,
		IndexerRecord indexer
	) {
		return Actions.getProvider(action.getActionType())
			.router()
			.route(new IndexerActionRouteContext(
				indexer.targetId(),
				indexer.id(),
				indexer.targetName(),
				indexer.indexName(),
				getQueueName(indexer),
				indexer.role()
			), action, IndexerActionRouteMode.DIRECT)
			.orElseThrow(() -> new IllegalArgumentException(
				"Action is not accepted by indexer: " + indexer.indexName()
			));
	}

	private String getQueueName(IndexerRecord indexer) {
		return indexer.queueName() == null ? indexer.indexName() : indexer.queueName();
	}

	private boolean hasPublicTarget(SubmitIndexActionsCommand submit) {
		return submit.getTargetName() != null;
	}

	private static class MetadataRoutingContext {
		private final Set<Integer> metadataChangedIndexerIds = new HashSet<>();

		private void markMetadataChanged(Integer indexerId) {
			metadataChangedIndexerIds.add(indexerId);
		}

		private boolean metadataChanged(Integer indexerId) {
			return metadataChangedIndexerIds.contains(indexerId);
		}
	}

	private record ResolvedTarget(
		TargetRecord record,
		boolean autoProvisionOnWrite
	) {
	}
}

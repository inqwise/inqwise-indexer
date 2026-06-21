package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.errors.RetryableStaleStateException;

import io.vertx.core.Future;

public class InMemoryDocumentStoreMetadataRepository implements DocumentStoreMetadataRepository {
	private final AtomicInteger targetIdSequence = new AtomicInteger();
	private final AtomicInteger indexerIdSequence = new AtomicInteger();
	private final AtomicInteger publicationIdSequence = new AtomicInteger();
	private final AtomicInteger manifestIdSequence = new AtomicInteger();
	private final Map<Integer, TargetRecord> targetsById = new ConcurrentHashMap<>();
	private final Map<Integer, IndexerRecord> indexersById = new ConcurrentHashMap<>();
	private final Map<Integer, PublicationRecord> publicationsById = new ConcurrentHashMap<>();
	private final Map<Integer, ManifestRecord> manifestsById = new ConcurrentHashMap<>();

	public synchronized Future<Integer> insertTarget(InsertTarget target) {
		try {
			TargetNameValidator.requireTargetName(target.targetName());
			requireUniqueTarget(target.targetName(), target.periodKey());

			Integer id = targetIdSequence.incrementAndGet();
			Instant now = Instant.now();
			targetsById.put(id, new TargetRecord(
				id,
				requirePrefix(target.prefix()),
				target.targetName(),
				target.periodKey(),
				target.periodStartInclusive(),
				target.periodEndExclusive(),
				require(target.status(), "status"),
				require(target.provisioningState(), "provisioningState"),
				now,
				now,
				0L
			));

			return Future.succeededFuture(id);
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public Future<Optional<TargetRecord>> getTargetById(Integer id) {
		return Future.succeededFuture(Optional.ofNullable(targetsById.get(id)));
	}

	@Override
	public Future<Optional<TargetRecord>> getTargetByUid(String uid) {
		return Future.succeededFuture(findByUid(targetsById, uid));
	}

	@Override
	public Future<Optional<TargetRecord>> getTargetByDefinitionAndPeriod(ConcreteTargetKey key) {
		return Future.succeededFuture(targetsById.values().stream()
			.filter(target -> key.targetName().equals(target.targetName()))
			.filter(target -> Objects.equals(key.periodKey(), target.periodKey()))
			.findFirst());
	}

	@Override
	public Future<List<TargetRecord>> listTargets(TargetMetadataQuery query) {
		TargetMetadataQuery resolvedQuery = query == null
			? new TargetMetadataQuery(null, null, null, null)
			: query;
		return Future.succeededFuture(targetsById.values().stream()
			.filter(target -> matches(resolvedQuery.ids(), target.id()))
			.filter(target -> matches(resolvedQuery.targetNames(), target.targetName()))
			.filter(target -> matches(resolvedQuery.statuses(), target.status()))
			.filter(target -> matches(resolvedQuery.provisioningStates(), target.provisioningState()))
			.sorted(Comparator.comparing(TargetRecord::id))
			.toList());
	}

	@Override
	public synchronized Future<TargetRecord> ensureTarget(
		String targetName,
		TargetPeriod period
	) {
		try {
			TargetNameValidator.requireTargetName(targetName);
			TargetPeriod resolvedPeriod = period == null
				? new TargetPeriod(TargetPeriodStrategy.NONE, null, null, null)
				: period;
			Optional<TargetRecord> existing = targetsById.values().stream()
				.filter(target -> targetName.equals(target.targetName()))
				.filter(target -> Objects.equals(resolvedPeriod.key(), target.periodKey()))
				.findFirst();

			if (existing.isPresent()) {
				return Future.succeededFuture(existing.get());
			}

			requireUniqueTarget(targetName, resolvedPeriod.key());

			Integer id = targetIdSequence.incrementAndGet();
			Instant now = Instant.now();
			TargetRecord created = new TargetRecord(
				id,
				"target",
				targetName,
				resolvedPeriod.key(),
				resolvedPeriod.startInclusive(),
				resolvedPeriod.endExclusive(),
				TargetStatus.ACTIVE,
				TargetProvisioningState.READY,
				now,
				now,
				0L
			);
			targetsById.put(id, created);
			return Future.succeededFuture(created);
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> updateTargetStatus(UpdateTargetStatus update) {
		try {
			TargetRecord existing = requireTarget(update.id(), update.expectedVersion());
			targetsById.put(update.id(), copyTarget(
				existing,
				require(update.status(), "status"),
				existing.provisioningState()
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> updateTargetProvisioningState(UpdateTargetProvisioningState update) {
		try {
			TargetRecord existing = requireTarget(update.id(), update.expectedVersion());
			targetsById.put(update.id(), copyTarget(
				existing,
				existing.status(),
				require(update.provisioningState(), "provisioningState")
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> deleteTarget(DeleteTarget delete) {
		try {
			requireTarget(delete.id(), delete.expectedVersion());
			targetsById.remove(delete.id());
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Integer> insertIndexer(InsertIndexer indexer) {
		try {
			require(indexer.targetId(), "targetId");
			require(indexer.targetName(), "targetName");
			require(indexer.indexName(), "indexName");

			Integer id = indexerIdSequence.incrementAndGet();
			Instant now = Instant.now();
			indexersById.put(id, new IndexerRecord(
				id,
				requirePrefix(indexer.prefix()),
				indexer.targetId(),
				indexer.targetName(),
				indexer.indexName(),
				indexer.queueName(),
				defaultValue(indexer.type(), IndexerType.INDEX),
				defaultValue(indexer.role(), IndexerRole.LIVE_WRITER),
				defaultValue(indexer.indexOwnership(), IndexResourceOwnership.OWNER),
				require(indexer.status(), "status"),
				require(indexer.provisioningState(), "provisioningState"),
				require(indexer.runtimeState(), "runtimeState"),
				require(indexer.publicationState(), "publicationState"),
				require(indexer.mutationState(), "mutationState"),
				now,
				now,
				0L
			));

			return Future.succeededFuture(id);
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public Future<Optional<IndexerRecord>> getIndexerById(Integer id) {
		return Future.succeededFuture(Optional.ofNullable(indexersById.get(id)));
	}

	@Override
	public Future<Optional<IndexerRecord>> getIndexerByUid(String uid) {
		return Future.succeededFuture(findByUid(indexersById, uid));
	}

	@Override
	public Future<List<IndexerRecord>> listIndexersByTargetId(Integer targetId) {
		return Future.succeededFuture(indexersById.values().stream()
			.filter(indexer -> targetId.equals(indexer.targetId()))
			.sorted(Comparator.comparing(IndexerRecord::id))
			.toList());
	}

	@Override
	public Future<List<IndexerRecord>> listIndexers(IndexerMetadataQuery query) {
		IndexerMetadataQuery resolvedQuery = query == null
			? new IndexerMetadataQuery(null, null, null, null, null, null, null, null, null)
			: query;
		return Future.succeededFuture(indexersById.values().stream()
			.filter(indexer -> matches(resolvedQuery.ids(), indexer.id()))
			.filter(indexer -> matches(resolvedQuery.targetIds(), indexer.targetId()))
			.filter(indexer -> matches(resolvedQuery.types(), indexer.type()))
			.filter(indexer -> matches(resolvedQuery.roles(), indexer.role()))
			.filter(indexer -> matches(resolvedQuery.statuses(), indexer.status()))
			.filter(indexer -> matches(resolvedQuery.provisioningStates(), indexer.provisioningState()))
			.filter(indexer -> matches(resolvedQuery.runtimeStates(), indexer.runtimeState()))
			.filter(indexer -> matches(resolvedQuery.publicationStates(), indexer.publicationState()))
			.filter(indexer -> matches(resolvedQuery.mutationStates(), indexer.mutationState()))
			.sorted(Comparator.comparing(IndexerRecord::id))
			.toList());
	}

	@Override
	public Future<List<IndexerRecord>> listWritableIndexersByTargetId(Integer targetId) {
		return Future.succeededFuture(indexersById.values().stream()
			.filter(indexer -> targetId.equals(indexer.targetId()))
			.filter(indexer -> indexer.status() == IndexerStatus.AVAILABLE)
			.filter(indexer -> indexer.provisioningState() == IndexerProvisioningState.READY)
			.filter(indexer -> indexer.mutationState() == MutationState.WRITABLE)
			.sorted(Comparator.comparing(IndexerRecord::id))
			.toList());
	}

	@Override
	public Future<List<IndexerRecord>> listPublishedIndexersByTargetId(Integer targetId) {
		return Future.succeededFuture(indexersById.values().stream()
			.filter(indexer -> targetId.equals(indexer.targetId()))
			.filter(indexer -> indexer.status() == IndexerStatus.AVAILABLE)
			.filter(indexer -> indexer.provisioningState() == IndexerProvisioningState.READY)
			.filter(indexer -> indexer.publicationState() == PublicationState.PUBLISHED)
			.sorted(Comparator.comparing(IndexerRecord::id))
			.toList());
	}

	@Override
	public Future<List<IndexerRecord>> listRuntimeActiveIndexers() {
		return Future.succeededFuture(indexersById.values().stream()
			.filter(indexer -> indexer.status() == IndexerStatus.AVAILABLE)
			.filter(indexer -> indexer.provisioningState() == IndexerProvisioningState.READY)
			.filter(indexer -> indexer.runtimeState() == IndexerRuntimeState.ACTIVE)
			.sorted(Comparator.comparing(IndexerRecord::id))
			.toList());
	}

	@Override
	public synchronized Future<Void> updateIndexerRuntimeState(UpdateIndexerRuntimeState update) {
		try {
			IndexerRecord existing = requireIndexer(update.id(), update.expectedVersion());
			indexersById.put(update.id(), copyIndexer(
				existing,
				existing.queueName(),
				existing.status(),
				existing.provisioningState(),
				require(update.runtimeState(), "runtimeState"),
				existing.publicationState(),
				existing.mutationState()
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> updateIndexerProvisioningState(UpdateIndexerProvisioningState update) {
		try {
			IndexerRecord existing = requireIndexer(update.id(), update.expectedVersion());
			indexersById.put(update.id(), copyIndexer(
				existing,
				existing.queueName(),
				existing.status(),
				require(update.provisioningState(), "provisioningState"),
				existing.runtimeState(),
				existing.publicationState(),
				existing.mutationState()
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> updateIndexerPublicationState(UpdateIndexerPublicationState update) {
		try {
			IndexerRecord existing = requireIndexer(update.id(), update.expectedVersion());
			indexersById.put(update.id(), copyIndexer(
				existing,
				existing.queueName(),
				existing.status(),
				existing.provisioningState(),
				existing.runtimeState(),
				require(update.publicationState(), "publicationState"),
				existing.mutationState()
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> replacePublishedIndexer(ReplacePublishedIndexer replace) {
		try {
			require(replace.targetId(), "targetId");
			IndexerRecord candidate = requireIndexer(
				replace.candidateIndexerId(),
				replace.expectedCandidateVersion()
			);
			if (!replace.targetId().equals(candidate.targetId())) {
				throw new IllegalStateException("Candidate target mismatch: " + candidate.id());
			}
			requireNotDeleting("Candidate", candidate);
			if (candidate.publicationState() != PublicationState.UNPUBLISHED) {
				throw new IllegalStateException("Candidate indexer is not unpublished: " + candidate.id());
			}

			List<IndexerRecord> published = indexersById.values().stream()
				.filter(indexer -> replace.targetId().equals(indexer.targetId()))
				.filter(indexer -> indexer.publicationState() == PublicationState.PUBLISHED)
				.sorted(Comparator.comparing(IndexerRecord::id))
				.toList();
			if (published.size() > 1) {
				throw new IllegalStateException("Multiple published indexers for target: " + replace.targetId());
			}

			IndexerRecord previous = published.isEmpty() ? null : published.get(0);
			if (replace.previousIndexerId() == null) {
				if (previous != null) {
					throw new IllegalStateException("Published indexer already exists for target: " + replace.targetId());
				}
			} else {
				if (previous == null || !replace.previousIndexerId().equals(previous.id())) {
					throw new IllegalStateException("Published indexer mismatch for target: " + replace.targetId());
				}
				if (replace.expectedPreviousVersion() == null) {
					throw new NullPointerException("expectedPreviousVersion");
				}
				requireVersion("Indexer", previous.id(), replace.expectedPreviousVersion(), previous.version());
				requireNotDeleting("Previous", previous);
			}

			IndexerRecord ownershipSource = null;
			if (replace.ownershipSourceIndexerId() != null) {
				if (replace.expectedOwnershipSourceVersion() == null) {
					throw new NullPointerException("expectedOwnershipSourceVersion");
				}
				ownershipSource = requireIndexer(
					replace.ownershipSourceIndexerId(),
					replace.expectedOwnershipSourceVersion()
				);
				if (!replace.targetId().equals(ownershipSource.targetId())
					|| !candidate.indexName().equals(ownershipSource.indexName())) {
					throw new IllegalStateException("Ownership source mismatch: " + ownershipSource.id());
				}
				requireNotDeleting("Ownership source", ownershipSource);
			}

			if (previous != null) {
				indexersById.put(previous.id(), copyIndexer(
					previous,
					previous.queueName(),
					previous.role(),
					previous.indexOwnership(),
					previous.status(),
					previous.provisioningState(),
					previous.runtimeState(),
					PublicationState.RETIRED,
					previous.mutationState()
				));
			}

			if (ownershipSource != null) {
				indexersById.put(ownershipSource.id(), copyIndexer(
					ownershipSource,
					ownershipSource.queueName(),
					ownershipSource.role(),
					IndexResourceOwnership.ATTACHED,
					ownershipSource.status(),
					ownershipSource.provisioningState(),
					ownershipSource.runtimeState(),
					ownershipSource.publicationState(),
					ownershipSource.mutationState()
				));
			}

			indexersById.put(candidate.id(), copyIndexer(
				candidate,
				candidate.queueName(),
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				candidate.status(),
				candidate.provisioningState(),
				candidate.runtimeState(),
				PublicationState.PUBLISHED,
				candidate.mutationState()
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> updateIndexerMutationState(UpdateIndexerMutationState update) {
		try {
			IndexerRecord existing = requireIndexer(update.id(), update.expectedVersion());
			if (existing.mutationState() == MutationState.DELETING
				&& update.mutationState() != MutationState.DELETING) {
				throw new IllegalStateException(
					"Deleting indexer mutation state is terminal: " + existing.id()
				);
			}
			indexersById.put(update.id(), copyIndexer(
				existing,
				existing.queueName(),
				existing.status(),
				existing.provisioningState(),
				existing.runtimeState(),
				existing.publicationState(),
				require(update.mutationState(), "mutationState")
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> updateIndexerQueueName(UpdateIndexerQueueName update) {
		try {
			IndexerRecord existing = requireIndexer(update.id(), update.expectedVersion());
			if (existing.mutationState() == MutationState.DELETING) {
				throw new IllegalStateException(
					"Deleting indexer queue identity is immutable: " + existing.id()
				);
			}
			indexersById.put(update.id(), copyIndexer(
				existing,
				require(update.queueName(), "queueName"),
				existing.status(),
				existing.provisioningState(),
				existing.runtimeState(),
				existing.publicationState(),
				existing.mutationState()
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> finalizeIndexerDeletion(
		FinalizeIndexerDeletion finalizeDeletion
	) {
		try {
			IndexerRecord existing = indexersById.get(finalizeDeletion.indexerId());
			if (existing == null) {
				return Future.succeededFuture();
			}

			if (existing.version() != finalizeDeletion.expectedVersion()) {
				throw new RetryableStaleStateException(
					"Indexer version conflict for id " + finalizeDeletion.indexerId()
						+ ": expected " + finalizeDeletion.expectedVersion()
						+ " but was " + existing.version()
				);
			}
			if (existing.mutationState() != MutationState.DELETING) {
				throw new IllegalStateException(
					"Indexer is not deleting: " + finalizeDeletion.indexerId()
				);
			}

			publicationsById.entrySet().removeIf(entry ->
				finalizeDeletion.indexerId().equals(entry.getValue().indexerId()));
			manifestsById.entrySet().removeIf(entry ->
				finalizeDeletion.indexerId().equals(entry.getValue().indexerId()));
			indexersById.remove(finalizeDeletion.indexerId());
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Integer> insertPublication(InsertPublication publication) {
		try {
			require(publication.indexerId(), "indexerId");
			require(publication.targetId(), "targetId");
			require(publication.targetName(), "targetName");
			require(publication.indexName(), "indexName");
			requireUniquePublicationIndexer(publication.indexerId(), null);

			Integer id = publicationIdSequence.incrementAndGet();
			Instant now = Instant.now();
			ReadinessState readinessState = defaultValue(
				publication.readinessState(),
				ReadinessState.PENDING
			);
			publicationsById.put(id, new PublicationRecord(
				id,
				requirePrefix(publication.prefix()),
				publication.indexerId(),
				publication.targetId(),
				publication.targetName(),
				publication.indexName(),
				readinessState,
				publication.reason(),
				readinessState == ReadinessState.READY ? now : null,
				now,
				now,
				0L
			));

			return Future.succeededFuture(id);
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public Future<Optional<PublicationRecord>> getPublicationById(Integer id) {
		return Future.succeededFuture(Optional.ofNullable(publicationsById.get(id)));
	}

	@Override
	public Future<Optional<PublicationRecord>> getPublicationByUid(String uid) {
		return Future.succeededFuture(findByUid(publicationsById, uid));
	}

	@Override
	public Future<Optional<PublicationRecord>> getPublicationByIndexerId(Integer indexerId) {
		return Future.succeededFuture(publicationsById.values().stream()
			.filter(publication -> indexerId.equals(publication.indexerId()))
			.findFirst());
	}

	@Override
	public Future<List<PublicationRecord>> listPublicationsByTargetId(Integer targetId) {
		return Future.succeededFuture(publicationsById.values().stream()
			.filter(publication -> targetId.equals(publication.targetId()))
			.sorted(Comparator.comparing(PublicationRecord::id))
			.toList());
	}

	@Override
	public synchronized Future<Void> updatePublicationReadiness(UpdatePublicationReadiness update) {
		try {
			PublicationRecord existing = requirePublication(update.id(), update.expectedVersion());
			ReadinessState readinessState = require(update.readinessState(), "readinessState");
			publicationsById.put(update.id(), new PublicationRecord(
				existing.id(),
				existing.prefix(),
				existing.indexerId(),
				existing.targetId(),
				existing.targetName(),
				existing.indexName(),
				readinessState,
				update.reason(),
				readinessState == ReadinessState.READY ? Instant.now() : existing.readyAt(),
				existing.createdAt(),
				Instant.now(),
				existing.version() + 1
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> deletePublication(DeletePublication delete) {
		try {
			requirePublication(delete.id(), delete.expectedVersion());
			publicationsById.remove(delete.id());
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Integer> insertManifest(InsertManifest manifest) {
		try {
			require(manifest.targetId(), "targetId");
			require(manifest.indexerId(), "indexerId");
			require(manifest.targetName(), "targetName");
			require(manifest.indexName(), "indexName");
			require(manifest.schemaName(), "schemaName");
			require(manifest.schemaVersion(), "schemaVersion");

			ManifestStatus status = defaultValue(manifest.status(), ManifestStatus.DRAFT);
			if (status == ManifestStatus.ACTIVE) {
				requireNoActiveManifest(manifest.indexerId(), null);
			}

			Integer id = manifestIdSequence.incrementAndGet();
			Instant now = Instant.now();
			manifestsById.put(id, new ManifestRecord(
				id,
				requirePrefix(manifest.prefix()),
				manifest.targetId(),
				manifest.indexerId(),
				manifest.targetName(),
				manifest.indexName(),
				manifest.schemaName(),
				manifest.schemaVersion(),
				manifest.manifest(),
				status,
				now,
				now,
				0L
			));

			return Future.succeededFuture(id);
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public Future<Optional<ManifestRecord>> getManifestById(Integer id) {
		return Future.succeededFuture(Optional.ofNullable(manifestsById.get(id)));
	}

	@Override
	public Future<Optional<ManifestRecord>> getManifestByUid(String uid) {
		return Future.succeededFuture(findByUid(manifestsById, uid));
	}

	@Override
	public Future<Optional<ManifestRecord>> getActiveManifestByIndexerId(Integer indexerId) {
		return Future.succeededFuture(manifestsById.values().stream()
			.filter(manifest -> indexerId.equals(manifest.indexerId()))
			.filter(manifest -> manifest.status() == ManifestStatus.ACTIVE)
			.findFirst());
	}

	@Override
	public Future<List<ManifestRecord>> listManifestsByTargetId(Integer targetId) {
		return Future.succeededFuture(manifestsById.values().stream()
			.filter(manifest -> targetId.equals(manifest.targetId()))
			.sorted(Comparator.comparing(ManifestRecord::id))
			.toList());
	}

	@Override
	public synchronized Future<Void> updateManifestStatus(UpdateManifestStatus update) {
		try {
			ManifestRecord existing = requireManifest(update.id(), update.expectedVersion());
			ManifestStatus status = require(update.status(), "status");
			if (status == ManifestStatus.ACTIVE) {
				requireNoActiveManifest(existing.indexerId(), existing.id());
			}

			manifestsById.put(update.id(), new ManifestRecord(
				existing.id(),
				existing.prefix(),
				existing.targetId(),
				existing.indexerId(),
				existing.targetName(),
				existing.indexName(),
				existing.schemaName(),
				existing.schemaVersion(),
				existing.manifest(),
				status,
				existing.createdAt(),
				Instant.now(),
				existing.version() + 1
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> deleteManifest(DeleteManifest delete) {
		try {
			requireManifest(delete.id(), delete.expectedVersion());
			manifestsById.remove(delete.id());
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	private IndexerRecord copyIndexer(
		IndexerRecord existing,
		String queueName,
		IndexerRole role,
		IndexResourceOwnership indexOwnership,
		IndexerStatus status,
		IndexerProvisioningState provisioningState,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		return new IndexerRecord(
			existing.id(),
			existing.prefix(),
			existing.targetId(),
			existing.targetName(),
			existing.indexName(),
			queueName,
			existing.type(),
			role,
			indexOwnership,
			status,
			provisioningState,
			runtimeState,
			publicationState,
			mutationState,
			existing.createdAt(),
			Instant.now(),
			existing.version() + 1
		);
	}

	private IndexerRecord copyIndexer(
		IndexerRecord existing,
		String queueName,
		IndexerStatus status,
		IndexerProvisioningState provisioningState,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		return copyIndexer(
			existing,
			queueName,
			existing.role(),
			existing.indexOwnership(),
			status,
			provisioningState,
			runtimeState,
			publicationState,
			mutationState
		);
	}

	private static <T> boolean matches(List<T> values, T value) {
		return values.isEmpty() || values.contains(value);
	}

	private TargetRecord copyTarget(
		TargetRecord existing,
		TargetStatus status,
		TargetProvisioningState provisioningState
	) {
		return new TargetRecord(
			existing.id(),
			existing.prefix(),
			existing.targetName(),
			existing.periodKey(),
			existing.periodStartInclusive(),
			existing.periodEndExclusive(),
			status,
			provisioningState,
			existing.createdAt(),
			Instant.now(),
			existing.version() + 1
		);
	}

	private TargetRecord requireTarget(Integer id, long expectedVersion) {
		TargetRecord existing = targetsById.get(id);
		if (existing == null) {
			throw new IllegalStateException("Target not found: " + id);
		}
		requireVersion("Target", id, expectedVersion, existing.version());
		return existing;
	}

	private IndexerRecord requireIndexer(Integer id, long expectedVersion) {
		IndexerRecord existing = indexersById.get(id);
		if (existing == null) {
			throw new IllegalStateException("Indexer not found: " + id);
		}
		requireVersion("Indexer", id, expectedVersion, existing.version());
		return existing;
	}

	private PublicationRecord requirePublication(Integer id, long expectedVersion) {
		PublicationRecord existing = publicationsById.get(id);
		if (existing == null) {
			throw new IllegalStateException("Publication not found: " + id);
		}
		requireVersion("Publication", id, expectedVersion, existing.version());
		return existing;
	}

	private ManifestRecord requireManifest(Integer id, long expectedVersion) {
		ManifestRecord existing = manifestsById.get(id);
		if (existing == null) {
			throw new IllegalStateException("Manifest not found: " + id);
		}
		requireVersion("Manifest", id, expectedVersion, existing.version());
		return existing;
	}

	private void requireVersion(String type, Integer id, long expectedVersion, long actualVersion) {
		if (expectedVersion != actualVersion) {
			throw new IllegalStateException(
				type + " version conflict for id " + id + ": expected "
					+ expectedVersion + " but was " + actualVersion
			);
		}
	}

	private void requireNotDeleting(String role, IndexerRecord indexer) {
		if (indexer.mutationState() == MutationState.DELETING) {
			throw new IllegalStateException(
				role + " indexer is deleting: " + indexer.id()
			);
		}
	}

	private void requireUniqueTarget(
		String targetName,
		String periodKey
	) {
		boolean exists = targetsById.values().stream()
			.anyMatch(target -> target.targetName().equals(targetName)
				&& Objects.equals(periodKey, target.periodKey()));

		if (exists) {
			throw new IllegalStateException("Target already exists: " + targetName);
		}
	}

	private void requireUniquePublicationIndexer(Integer indexerId, Integer currentId) {
		boolean exists = publicationsById.values().stream()
			.anyMatch(publication -> indexerId.equals(publication.indexerId())
				&& !publication.id().equals(currentId));

		if (exists) {
			throw new IllegalStateException("Publication already exists for indexer: " + indexerId);
		}
	}

	private void requireNoActiveManifest(Integer indexerId, Integer currentId) {
		boolean exists = manifestsById.values().stream()
			.anyMatch(manifest -> indexerId.equals(manifest.indexerId())
				&& manifest.status() == ManifestStatus.ACTIVE
				&& !manifest.id().equals(currentId));

		if (exists) {
			throw new IllegalStateException("Active manifest already exists for indexer: " + indexerId);
		}
	}

	private <T> Optional<T> findByUid(Map<Integer, T> records, String uid) {
		MetadataUid.Parsed parsed;
		try {
			parsed = MetadataUid.parse(uid);
		} catch (RuntimeException ignored) {
			return records.values().stream()
				.filter(record -> uid.equals(prefix(record)))
				.findFirst();
		}

		T record = records.get(parsed.id());
		if (record == null) {
			return Optional.empty();
		}

		String prefix = prefix(record);
		return parsed.prefix().equals(prefix) ? Optional.of(record) : Optional.empty();
	}

	private <T> String prefix(T record) {
		if (record instanceof TargetRecord target) {
			return target.prefix();
		}
		if (record instanceof IndexerRecord indexer) {
			return indexer.prefix();
		}
		if (record instanceof PublicationRecord publication) {
			return publication.prefix();
		}
		if (record instanceof ManifestRecord manifest) {
			return manifest.prefix();
		}
		throw new IllegalArgumentException("Unsupported metadata record: " + record);
	}

	private String requirePrefix(String prefix) {
		require(prefix, "prefix");
		if (!prefix.matches("[a-z][a-z0-9_-]{2,63}")) {
			throw new IllegalArgumentException("Invalid metadata prefix: " + prefix);
		}
		return prefix;
	}

	private <T> T defaultValue(T value, T defaultValue) {
		return value == null ? defaultValue : value;
	}

	private <T> T require(T value, String name) {
		if (value == null) {
			throw new NullPointerException(name);
		}
		return value;
	}
}

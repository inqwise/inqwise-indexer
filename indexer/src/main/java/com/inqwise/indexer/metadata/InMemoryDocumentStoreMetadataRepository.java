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

import io.vertx.core.Future;

public class InMemoryDocumentStoreMetadataRepository implements DocumentStoreMetadataRepository {
	private final AtomicInteger targetDefinitionIdSequence = new AtomicInteger();
	private final AtomicInteger targetIdSequence = new AtomicInteger();
	private final AtomicInteger indexerIdSequence = new AtomicInteger();
	private final AtomicInteger publicationIdSequence = new AtomicInteger();
	private final AtomicInteger manifestIdSequence = new AtomicInteger();
	private final Map<Integer, TargetDefinitionRecord> targetDefinitionsById = new ConcurrentHashMap<>();
	private final Map<Integer, TargetRecord> targetsById = new ConcurrentHashMap<>();
	private final Map<Integer, IndexerRecord> indexersById = new ConcurrentHashMap<>();
	private final Map<Integer, PublicationRecord> publicationsById = new ConcurrentHashMap<>();
	private final Map<Integer, ManifestRecord> manifestsById = new ConcurrentHashMap<>();
	private final TargetNameEncoder targetNameEncoder = new TargetNameEncoder();

	@Override
	public synchronized Future<Integer> insertTargetDefinition(InsertTargetDefinition targetDefinition) {
		try {
			TargetNameValidator.requireTargetName(targetDefinition.targetName());
			requireUniqueTargetDefinition(targetDefinition.targetName());

			Integer id = targetDefinitionIdSequence.incrementAndGet();
			Instant now = Instant.now();
			targetDefinitionsById.put(id, new TargetDefinitionRecord(
				id,
				requirePrefix(targetDefinition.prefix()),
				targetDefinition.targetName(),
				defaultValue(targetDefinition.periodStrategy(), TargetPeriodStrategy.NONE),
				defaultValue(targetDefinition.status(), TargetStatus.ACTIVE),
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
	public Future<Optional<TargetDefinitionRecord>> getTargetDefinitionById(Integer id) {
		return Future.succeededFuture(Optional.ofNullable(targetDefinitionsById.get(id)));
	}

	@Override
	public Future<Optional<TargetDefinitionRecord>> getTargetDefinitionByUid(String uid) {
		return Future.succeededFuture(findByUid(targetDefinitionsById, uid));
	}

	@Override
	public Future<Optional<TargetDefinitionRecord>> getTargetDefinitionByName(String targetName) {
		return Future.succeededFuture(targetDefinitionsById.values().stream()
			.filter(target -> target.targetName().equals(targetName))
			.findFirst());
	}

	@Override
	public synchronized Future<Integer> insertTarget(InsertTarget target) {
		try {
			TargetNameValidator.requireTargetName(target.targetName());
			requireUniqueTarget(target.targetName(), target.targetDefinitionId(), target.periodKey());

			Integer id = targetIdSequence.incrementAndGet();
			Instant now = Instant.now();
			targetsById.put(id, new TargetRecord(
				id,
				requirePrefix(target.prefix()),
				target.targetDefinitionId(),
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
			.filter(target -> key.targetDefinitionId().equals(target.targetDefinitionId()))
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
			.filter(target -> matches(resolvedQuery.targetDefinitionIds(), target.targetDefinitionId()))
			.filter(target -> matches(resolvedQuery.statuses(), target.status()))
			.filter(target -> matches(resolvedQuery.provisioningStates(), target.provisioningState()))
			.sorted(Comparator.comparing(TargetRecord::id))
			.toList());
	}

	@Override
	public synchronized Future<TargetRecord> ensureTarget(
		TargetDefinitionRecord targetDefinition,
		TargetPeriod period
	) {
		try {
			require(targetDefinition, "targetDefinition");
			TargetPeriod resolvedPeriod = period == null
				? new TargetPeriod(TargetPeriodStrategy.NONE, null, null, null)
				: period;
			Optional<TargetRecord> existing = targetsById.values().stream()
				.filter(target -> targetDefinition.id().equals(target.targetDefinitionId()))
				.filter(target -> Objects.equals(resolvedPeriod.key(), target.periodKey()))
				.findFirst();

			if (existing.isPresent()) {
				return Future.succeededFuture(existing.get());
			}

			String targetName = targetNameEncoder.concreteTargetName(
				targetDefinition.targetName(),
				resolvedPeriod
			);
			requireUniqueTarget(targetName, targetDefinition.id(), resolvedPeriod.key());

			Integer id = targetIdSequence.incrementAndGet();
			Instant now = Instant.now();
			TargetRecord created = new TargetRecord(
				id,
				targetDefinition.prefix(),
				targetDefinition.id(),
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
			? new IndexerMetadataQuery(null, null, null, null, null, null, null, null)
			: query;
		return Future.succeededFuture(indexersById.values().stream()
			.filter(indexer -> matches(resolvedQuery.ids(), indexer.id()))
			.filter(indexer -> matches(resolvedQuery.targetIds(), indexer.targetId()))
			.filter(indexer -> matches(resolvedQuery.types(), indexer.type()))
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
	public synchronized Future<Void> updateIndexerMutationState(UpdateIndexerMutationState update) {
		try {
			IndexerRecord existing = requireIndexer(update.id(), update.expectedVersion());
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
	public synchronized Future<Void> deleteIndexer(DeleteIndexer delete) {
		try {
			requireIndexer(delete.id(), delete.expectedVersion());
			indexersById.remove(delete.id());
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
			existing.role(),
			existing.indexOwnership(),
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
			existing.targetDefinitionId(),
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

	private void requireUniqueTargetDefinition(String targetName) {
		boolean exists = targetDefinitionsById.values().stream()
			.anyMatch(target -> target.targetName().equals(targetName));

		if (exists) {
			throw new IllegalStateException("Target definition already exists: " + targetName);
		}
	}

	private void requireUniqueTarget(
		String targetName,
		Integer targetDefinitionId,
		String periodKey
	) {
		boolean exists = targetsById.values().stream()
			.anyMatch(target -> target.targetName().equals(targetName)
				|| (targetDefinitionId != null
					&& targetDefinitionId.equals(target.targetDefinitionId())
					&& Objects.equals(periodKey, target.periodKey())));

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
		if (record instanceof TargetDefinitionRecord targetDefinition) {
			return targetDefinition.prefix();
		}
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

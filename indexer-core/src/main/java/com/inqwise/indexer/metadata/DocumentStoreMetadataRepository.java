package com.inqwise.indexer.metadata;

import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.catalog.targets.ConcreteTargetKey;
import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetPeriod;
import com.inqwise.indexer.catalog.indexers.MutationState;

import io.vertx.core.Future;

public interface DocumentStoreMetadataRepository {
	Future<Integer> insertTarget(InsertTarget target);

	Future<Optional<TargetRecord>> getTargetById(Integer id);

	Future<Optional<TargetRecord>> getTargetByUid(String uid);

	Future<Optional<TargetRecord>> getTargetByDefinitionAndPeriod(ConcreteTargetKey key);

	Future<List<TargetRecord>> listTargets(TargetCatalogQuery query);

	Future<TargetRecord> ensureTarget(String targetName, TargetPeriod period);

	Future<Void> updateTargetStatus(UpdateTargetStatus update);

	Future<Void> updateTargetProvisioningState(UpdateTargetProvisioningState update);

	Future<Void> deleteTarget(DeleteTarget delete);

	Future<Integer> insertIndexer(InsertIndexer indexer);

	Future<Optional<IndexerRecord>> getIndexerById(Integer id);

	Future<Optional<IndexerRecord>> getIndexerByUid(String uid);

	Future<List<IndexerRecord>> listIndexersByTargetId(Integer targetId);

	Future<List<IndexerRecord>> listIndexers(IndexerMetadataQuery query);

	Future<List<IndexerRecord>> listWritableIndexersByTargetId(Integer targetId);

	Future<List<IndexerRecord>> listPublishedIndexersByTargetId(Integer targetId);

	Future<List<IndexerRecord>> listRuntimeActiveIndexers();

	Future<Void> updateIndexerRuntimeState(UpdateIndexerRuntimeState update);

	Future<Void> updateIndexerProvisioningState(UpdateIndexerProvisioningState update);

	Future<Void> updateIndexerPublicationState(UpdateIndexerPublicationState update);

	/**
	 * Atomically replaces the published indexer and optionally transfers physical-index
	 * ownership. Candidate, previous, and ownership-source indexers must not be deleting;
	 * ownership is immutable after deletion begins.
	 */
	Future<Void> replacePublishedIndexer(ReplacePublishedIndexer replace);

	/**
	 * Updates indexer mutation state. {@link MutationState#DELETING} is terminal and
	 * cannot transition back to a writable or read-only state.
	 */
	Future<Void> updateIndexerMutationState(UpdateIndexerMutationState update);

	/**
	 * Updates queue identity before deletion starts. Queue identity is immutable while
	 * the indexer is {@link MutationState#DELETING} so physical cleanup can be retried
	 * from a fresh metadata snapshot.
	 */
	Future<Void> updateIndexerQueueName(UpdateIndexerQueueName update);

	/**
	 * Atomically removes a deleting indexer and all publication and manifest metadata.
	 * Missing indexers are successful idempotent cleanup misses. Existing indexers must
	 * match the expected version and be in {@link MutationState#DELETING}; version
	 * conflicts fail with a retryable stale-state error.
	 */
	Future<Void> finalizeIndexerDeletion(FinalizeIndexerDeletion finalizeDeletion);

	Future<Integer> insertPublication(InsertPublication publication);

	Future<Optional<PublicationRecord>> getPublicationById(Integer id);

	Future<Optional<PublicationRecord>> getPublicationByUid(String uid);

	Future<Optional<PublicationRecord>> getPublicationByIndexerId(Integer indexerId);

	Future<List<PublicationRecord>> listPublicationsByTargetId(Integer targetId);

	Future<Void> updatePublicationReadiness(UpdatePublicationReadiness update);

	Future<Void> deletePublication(DeletePublication delete);

	Future<Integer> insertManifest(InsertManifest manifest);

	Future<Optional<ManifestRecord>> getManifestById(Integer id);

	Future<Optional<ManifestRecord>> getManifestByUid(String uid);

	Future<Optional<ManifestRecord>> getActiveManifestByIndexerId(Integer indexerId);

	Future<List<ManifestRecord>> listManifestsByTargetId(Integer targetId);

	Future<Void> updateManifestStatus(UpdateManifestStatus update);

	Future<Void> deleteManifest(DeleteManifest delete);
}

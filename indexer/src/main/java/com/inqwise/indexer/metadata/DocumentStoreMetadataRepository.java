package com.inqwise.indexer.metadata;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

public interface DocumentStoreMetadataRepository {
	Future<Integer> insertTargetDefinition(InsertTargetDefinition targetDefinition);

	Future<Optional<TargetDefinitionRecord>> getTargetDefinitionById(Integer id);

	Future<Optional<TargetDefinitionRecord>> getTargetDefinitionByUid(String uid);

	Future<Optional<TargetDefinitionRecord>> getTargetDefinitionByName(String targetName);

	Future<Integer> insertTarget(InsertTarget target);

	Future<Optional<TargetRecord>> getTargetById(Integer id);

	Future<Optional<TargetRecord>> getTargetByUid(String uid);

	Future<Optional<TargetRecord>> getTargetByDefinitionAndPeriod(ConcreteTargetKey key);

	Future<TargetRecord> ensureTarget(TargetDefinitionRecord targetDefinition, TargetPeriod period);

	Future<Void> updateTargetStatus(UpdateTargetStatus update);

	Future<Void> updateTargetProvisioningState(UpdateTargetProvisioningState update);

	Future<Void> deleteTarget(DeleteTarget delete);

	Future<Integer> insertIndexer(InsertIndexer indexer);

	Future<Optional<IndexerRecord>> getIndexerById(Integer id);

	Future<Optional<IndexerRecord>> getIndexerByUid(String uid);

	Future<List<IndexerRecord>> listIndexersByTargetId(Integer targetId);

	Future<List<IndexerRecord>> listWritableIndexersByTargetId(Integer targetId);

	Future<List<IndexerRecord>> listPublishedIndexersByTargetId(Integer targetId);

	Future<List<IndexerRecord>> listRuntimeActiveIndexers();

	Future<Void> updateIndexerRuntimeStatus(UpdateIndexerRuntimeStatus update);

	Future<Void> updateIndexerPublicationState(UpdateIndexerPublicationState update);

	Future<Void> updateIndexerMutationState(UpdateIndexerMutationState update);

	Future<Void> updateIndexerQueueName(UpdateIndexerQueueName update);

	Future<Void> deleteIndexer(DeleteIndexer delete);

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

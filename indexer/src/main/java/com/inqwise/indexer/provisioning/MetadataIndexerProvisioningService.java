package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertManifest;
import com.inqwise.indexer.metadata.InsertPublication;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.UpdateIndexerProvisioningState;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionRequest;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.ReadinessState;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class MetadataIndexerProvisioningService implements IndexerProvisioningService {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerDefinitionProvider definitionProvider;
	private final IndexerDocumentIndexResourceManager documentIndexResources;
	private final IndexerQueueResourceManager queueResources;

	public MetadataIndexerProvisioningService(
		DocumentStoreMetadataRepository repository,
		IndexerDefinitionProvider definitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.definitionProvider = Objects.requireNonNull(definitionProvider, "definitionProvider");
		this.documentIndexResources = documentIndexResources == null
			? IndexerDocumentIndexResourceManager.NOOP
			: documentIndexResources;
		this.queueResources = queueResources == null
			? IndexerQueueResourceManager.NOOP
			: queueResources;
	}

	@Override
	public Future<ProvisionedIndexer> createIndexer(CreateIndexerProvisioningRequest request) {
		Objects.requireNonNull(request, "request");
		return getTarget(request.targetId()).compose(target -> provision(request, target));
	}

	private Future<ProvisionedIndexer> provision(
		CreateIndexerProvisioningRequest request,
		TargetRecord target
	) {
		return definitionProvider.get(new IndexerDefinitionRequest(
			target.id(),
			target.targetName(),
			IndexerType.INDEX,
			request.role(),
			request.indexOwnership()
		)).compose(definition -> insertProvisioning(request, target)
			.compose(indexer -> ensureResources(indexer, definition)
				.compose(ignored -> insertManifest(indexer, definition))
				.compose(ignored -> insertPublication(indexer))
				.compose(ignored -> markReady(indexer))
				.recover(error -> markFailed(indexer).compose(ignored -> Future.failedFuture(error)))))
			.map(indexer -> new ProvisionedIndexer(
				indexer.id(),
				indexer.targetId(),
				indexer.version()
			));
	}

	private Future<IndexerRecord> insertProvisioning(
		CreateIndexerProvisioningRequest request,
		TargetRecord target
	) {
		return repository.insertIndexer(InsertIndexer.builder()
			.withPrefix(request.prefix())
			.withTargetId(target.id())
			.withTargetName(target.targetName())
			.withIndexName(request.indexName())
			.withQueueName(request.queueName())
			.withType(IndexerType.INDEX)
			.withRole(request.role())
			.withIndexOwnership(request.indexOwnership())
			.withStatus(IndexerStatus.AVAILABLE)
			.withProvisioningState(IndexerProvisioningState.PROVISIONING)
			.withRuntimeState(request.runtimeState())
			.withPublicationState(PublicationState.UNPUBLISHED)
			.withMutationState(MutationState.WRITABLE)
			.build()).compose(this::getIndexer);
	}

	private Future<Void> ensureResources(IndexerRecord indexer, IndexerDefinition definition) {
		return documentIndexResources.ensure(indexer.indexName(), definition.index())
			.compose(ignored -> queueResources.ensure(indexer.queueName(), definition.queue()));
	}

	private Future<Integer> insertManifest(IndexerRecord indexer, IndexerDefinition definition) {
		return repository.insertManifest(new InsertManifest(
			indexer.prefix(),
			indexer.targetId(),
			indexer.id(),
			indexer.targetName(),
			indexer.indexName(),
			definition.index().schemaName(),
			definition.index().schemaVersion(),
			new JsonObject()
				.put("index", new JsonObject()
					.put("settings", definition.index().settings())
					.put("mappings", definition.index().mappings()))
				.put("queue", new JsonObject()
					.put("settings", definition.queue().settings())),
			ManifestStatus.ACTIVE
		));
	}

	private Future<Integer> insertPublication(IndexerRecord indexer) {
		return repository.insertPublication(new InsertPublication(
			indexer.prefix(),
			indexer.id(),
			indexer.targetId(),
			indexer.targetName(),
			indexer.indexName(),
			ReadinessState.PENDING,
			"indexer created"
		));
	}

	private Future<IndexerRecord> markReady(IndexerRecord indexer) {
		return repository.updateIndexerProvisioningState(new UpdateIndexerProvisioningState(
			indexer.id(),
			IndexerProvisioningState.READY,
			indexer.version()
		)).compose(ignored -> getIndexer(indexer.id()));
	}

	private Future<Void> markFailed(IndexerRecord indexer) {
		return repository.updateIndexerProvisioningState(new UpdateIndexerProvisioningState(
			indexer.id(),
			IndexerProvisioningState.FAILED,
			indexer.version()
		));
	}

	private Future<IndexerRecord> getIndexer(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexerId)));
	}

	private Future<TargetRecord> getTarget(Integer targetId) {
		return repository.getTargetById(targetId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Target not found: " + targetId)));
	}

}

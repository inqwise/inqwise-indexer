package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.IndexerDefinitionRequest;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertManifest;
import com.inqwise.indexer.metadata.InsertPublication;
import com.inqwise.indexer.metadata.ManifestStatus;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerProvisioningState;

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
	public Future<IndexerRecord> createIndexer(CreateIndexerProvisioningRequest request) {
		Objects.requireNonNull(request, "request");
		return definitionProvider.get(new IndexerDefinitionRequest(
			request.targetId(),
			request.targetName(),
			request.indexerType(),
			request.role(),
			request.indexOwnership()
		)).compose(definition -> insertProvisioning(request)
			.compose(indexer -> ensureResources(indexer, definition)
				.compose(ignored -> insertManifest(indexer, definition))
				.compose(ignored -> insertPublication(indexer))
				.compose(ignored -> markReady(indexer))
				.recover(error -> markFailed(indexer).compose(ignored -> Future.failedFuture(error)))));
	}

	private Future<IndexerRecord> insertProvisioning(CreateIndexerProvisioningRequest request) {
		return repository.insertIndexer(new InsertIndexer(
			request.prefix(),
			request.targetId(),
			request.targetName(),
			request.indexName(),
			queueName(request),
			request.indexerType(),
			request.role(),
			request.indexOwnership(),
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.PROVISIONING,
			request.runtimeState(),
			request.publicationState(),
			request.mutationState()
		)).compose(this::getIndexer);
	}

	private Future<Void> ensureResources(IndexerRecord indexer, IndexerDefinition definition) {
		return documentIndexResources.ensure(indexer.indexName(), definition.index())
			.compose(ignored -> queueResources.ensure(queueName(indexer), definition.queue()));
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

	private String queueName(CreateIndexerProvisioningRequest request) {
		return request.queueName() == null ? request.indexName() : request.queueName();
	}

	private String queueName(IndexerRecord indexer) {
		return indexer.queueName() == null ? indexer.indexName() : indexer.queueName();
	}
}

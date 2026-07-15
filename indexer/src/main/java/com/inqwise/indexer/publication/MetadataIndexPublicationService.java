package com.inqwise.indexer.publication;

import java.util.Objects;

import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.IndexerDefinitionRequest;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.PublicationRecord;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerPublicationState;
import com.inqwise.indexer.metadata.UpdatePublicationReadiness;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;

public final class MetadataIndexPublicationService implements IndexPublicationService {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerDefinitionProvider definitionProvider;
	private final IndexerDocumentIndexResourceManager documentIndexResources;
	private final IndexerQueueResourceManager queueResources;

	public MetadataIndexPublicationService(
		DocumentStoreMetadataRepository repository,
		IndexerDefinitionProvider definitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.definitionProvider = Objects.requireNonNull(definitionProvider, "definitionProvider");
		this.documentIndexResources = Objects.requireNonNull(
			documentIndexResources,
			"documentIndexResources"
		);
		this.queueResources = Objects.requireNonNull(queueResources, "queueResources");
	}

	@Override
	public Future<PublicationRecord> markReady(MarkIndexReadyRequest request) {
		Objects.requireNonNull(request, "request");
		return repository.getPublicationById(request.publicationId())
			.compose(found -> found
				.map(publication -> markReady(request, publication))
				.orElseGet(() -> Future.failedFuture(
					"Publication not found: " + request.publicationId()
				)));
	}

	@Override
	public Future<IndexerRecord> publish(PublishIndexRequest request) {
		Objects.requireNonNull(request, "request");
		return loadIndexer(request.indexerId())
			.compose(indexer -> repository.getPublicationByIndexerId(indexer.id())
				.compose(found -> found
					.map(publication -> publish(request, indexer, publication))
					.orElseGet(() -> Future.failedFuture(
						"Publication not found for indexer: " + indexer.id()
					))));
	}

	@Override
	public Future<IndexerRecord> retire(RetireIndexRequest request) {
		Objects.requireNonNull(request, "request");
		return loadIndexer(request.indexerId()).compose(indexer -> {
			if (alreadyRetired(request, indexer)) {
				return Future.succeededFuture(indexer);
			}
			if (indexer.version() != request.expectedVersion()) {
				return Future.failedFuture(indexerVersionConflict(indexer, request.expectedVersion()));
			}
			if (indexer.publicationState() == PublicationState.RETIRED) {
				return Future.failedFuture("Index is already retired: " + indexer.indexName());
			}
			return repository.updateIndexerPublicationState(new UpdateIndexerPublicationState(
				indexer.id(),
				PublicationState.RETIRED,
				request.expectedVersion()
			)).compose(ignored -> loadIndexer(indexer.id()));
		});
	}

	private Future<PublicationRecord> markReady(
		MarkIndexReadyRequest request,
		PublicationRecord publication
	) {
		if (alreadyMarkedReady(request, publication)) {
			return Future.succeededFuture(publication);
		}
		if (publication.version() != request.expectedVersion()) {
			return Future.failedFuture(
				"Publication version conflict for id " + publication.id() + ": expected "
					+ request.expectedVersion() + " but was " + publication.version()
			);
		}
		return repository.updatePublicationReadiness(new UpdatePublicationReadiness(
			publication.id(),
			ReadinessState.READY,
			request.reason(),
			request.expectedVersion()
		)).compose(ignored -> loadPublication(publication.id()));
	}

	private Future<IndexerRecord> publish(
		PublishIndexRequest request,
		IndexerRecord indexer,
		PublicationRecord publication
	) {
		if (alreadyPublished(request, indexer)) {
			return validateReadyState(indexer, publication).compose(this::ensureResources);
		}
		return validatePublish(request, indexer, publication)
			.compose(this::ensureResources)
			.compose(ready -> repository.updateIndexerPublicationState(
				new UpdateIndexerPublicationState(
					ready.id(),
					PublicationState.PUBLISHED,
					request.expectedVersion()
				)
			).compose(ignored -> loadIndexer(ready.id())));
	}

	private Future<IndexerRecord> ensureResources(IndexerRecord indexer) {
		return definitionProvider.get(new IndexerDefinitionRequest(
			indexer.targetId(),
			indexer.targetName(),
			indexer.type(),
			indexer.role(),
			indexer.indexOwnership()
		)).compose(definition -> documentIndexResources.ensure(
			indexer.indexName(),
			definition.index()
		).compose(ignored -> queueResources.ensure(
			indexer.queueName(),
			definition.queue()
		)).map(indexer));
	}

	private Future<IndexerRecord> validatePublish(
		PublishIndexRequest request,
		IndexerRecord indexer,
		PublicationRecord publication
	) {
		if (indexer.version() != request.expectedVersion()) {
			return Future.failedFuture(indexerVersionConflict(indexer, request.expectedVersion()));
		}
		if (indexer.publicationState() != PublicationState.UNPUBLISHED) {
			return Future.failedFuture("Index is not unpublished: " + indexer.indexName());
		}
		return validateReadyState(indexer, publication);
	}

	private Future<IndexerRecord> validateReadyState(
		IndexerRecord indexer,
		PublicationRecord publication
	) {
		if (publication.readinessState() != ReadinessState.READY) {
			return Future.failedFuture("Index is not ready: " + indexer.indexName());
		}
		if (indexer.mutationState() == MutationState.DELETING) {
			return Future.failedFuture("Index is deleting: " + indexer.indexName());
		}
		if (indexer.status() != IndexerStatus.AVAILABLE
			|| indexer.provisioningState() != IndexerProvisioningState.READY) {
			return Future.failedFuture("Indexer is not active: " + indexer.indexName());
		}
		return Future.succeededFuture(indexer);
	}

	private boolean alreadyMarkedReady(
		MarkIndexReadyRequest request,
		PublicationRecord publication
	) {
		return hasNextVersion(request.expectedVersion(), publication.version())
			&& publication.readinessState() == ReadinessState.READY
			&& Objects.equals(publication.reason(), request.reason());
	}

	private boolean alreadyPublished(PublishIndexRequest request, IndexerRecord indexer) {
		return hasNextVersion(request.expectedVersion(), indexer.version())
			&& indexer.publicationState() == PublicationState.PUBLISHED;
	}

	private boolean alreadyRetired(RetireIndexRequest request, IndexerRecord indexer) {
		return hasNextVersion(request.expectedVersion(), indexer.version())
			&& indexer.publicationState() == PublicationState.RETIRED;
	}

	private boolean hasNextVersion(long expectedVersion, long actualVersion) {
		return expectedVersion >= 0L
			&& expectedVersion < Long.MAX_VALUE
			&& actualVersion == expectedVersion + 1L;
	}

	private String indexerVersionConflict(IndexerRecord indexer, long expectedVersion) {
		return "Indexer version conflict for id " + indexer.id() + ": expected "
			+ expectedVersion + " but was " + indexer.version();
	}

	private Future<IndexerRecord> loadIndexer(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexerId)));
	}

	private Future<PublicationRecord> loadPublication(Integer publicationId) {
		return repository.getPublicationById(publicationId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Publication not found: " + publicationId)));
	}
}

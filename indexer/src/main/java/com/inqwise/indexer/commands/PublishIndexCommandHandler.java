package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.IndexerDefinitionRequest;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationRecord;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerPublicationState;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;

public class PublishIndexCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerDefinitionProvider definitionProvider;
	private final IndexerDocumentIndexResourceManager documentIndexResources;
	private final IndexerQueueResourceManager queueResources;

	public PublishIndexCommandHandler(
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
	public String getType() {
		return PublishIndexCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		PublishIndexCommand publish = new PublishIndexCommand(command.toJson());

		return repository.getIndexerById(publish.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Indexer not found: " + publish.getIndexerId()
				)))
			.compose(indexer -> repository.getPublicationByIndexerId(indexer.id())
				.compose(found -> found
					.map(publication -> publish(publish, indexer, publication))
					.orElseGet(() -> Future.failedFuture(
						"Publication not found for indexer: " + indexer.id()
					))));
	}

	private Future<Void> publish(
		PublishIndexCommand publish,
		IndexerRecord indexer,
		PublicationRecord publication
	) {
		if (alreadyApplied(publish, indexer)) {
			return validateReadyState(indexer, publication)
				.compose(this::ensureResources)
				.mapEmpty();
		}

		return validatePublish(publish, indexer, publication)
			.compose(this::ensureResources)
			.compose(ready -> repository.updateIndexerPublicationState(
				new UpdateIndexerPublicationState(
					ready.id(),
					PublicationState.PUBLISHED,
					publish.getExpectedVersion()
				)
			));
	}

	private boolean alreadyApplied(PublishIndexCommand publish, IndexerRecord indexer) {
		return publish.getExpectedVersion() >= 0L
			&& publish.getExpectedVersion() < Long.MAX_VALUE
			&& indexer.version() == publish.getExpectedVersion() + 1L
			&& indexer.publicationState() == PublicationState.PUBLISHED;
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
		PublishIndexCommand publish,
		IndexerRecord indexer,
		PublicationRecord publication
	) {
		if (indexer.version() != publish.getExpectedVersion()) {
			return Future.failedFuture(
				"Indexer version conflict for id " + indexer.id() + ": expected "
					+ publish.getExpectedVersion() + " but was " + indexer.version()
			);
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
}

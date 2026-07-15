package com.inqwise.indexer.service.admin;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;

import io.vertx.core.Future;

public class AdminCreateRequestResolver {
	private final DocumentStoreMetadataRepository repository;

	public AdminCreateRequestResolver(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	public AdminCreateTargetRequest target(
		String targetName,
		Instant timestamp,
		InitialPublicationMode initialPublicationMode
	) {
		AdminCreateTargetRequest request = new AdminCreateTargetRequest()
			.setPrefix(IndexerResourceNameGenerator.targetPrefix())
			.setTargetName(targetName)
			.setTimestamp(timestamp);

		if (initialPublicationMode != null) {
			GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(targetName);
			request.setCreateIndexer(new AdminCreateTargetIndexerRequest()
				.setPrefix(resources.prefix())
				.setIndexName(resources.indexName())
				.setQueueName(resources.queueName())
				.setInitialPublicationMode(initialPublicationMode));
		}

		return request;
	}

	public Future<AdminCreateIndexerRequest> indexer(Integer targetId) {
		if (targetId == null) {
			return Future.failedFuture(IndexerErrors.invalidRequest("Target id is required"));
		}

		return repository.getTargetById(targetId)
			.map(found -> found
				.map(this::indexer)
				.orElseThrow(() -> IndexerErrors.notFound("Target not found: " + targetId)));
	}

	private AdminCreateIndexerRequest indexer(TargetRecord target) {
		GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(target.targetName());
		return new AdminCreateIndexerRequest()
			.setPrefix(resources.prefix())
			.setTargetId(target.id())
			.setTargetName(target.targetName())
			.setIndexName(resources.indexName())
			.setQueueName(resources.queueName());
	}
}

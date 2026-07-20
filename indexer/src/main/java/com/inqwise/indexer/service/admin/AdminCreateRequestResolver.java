package com.inqwise.indexer.service.admin;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.InitialPublicationMode;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;
import com.inqwise.indexer.service.IndexerErrors;

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
		AdminCreateTargetRequest.Builder request = AdminCreateTargetRequest.builder()
			.withTargetName(targetName)
			.withTimestamp(timestamp);

		if (initialPublicationMode != null) {
			GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(targetName);
			request.withCreateIndexer(AdminCreateTargetIndexerRequest.builder()
				.withPrefix(resources.prefix())
				.withIndexName(resources.indexName())
				.withQueueName(resources.queueName())
				.withInitialPublicationMode(initialPublicationMode)
				.build());
		}

		return request.build();
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
		return AdminCreateIndexerRequest.builder()
			.withPrefix(resources.prefix())
			.withTargetId(target.id())
			.withIndexName(resources.indexName())
			.withQueueName(resources.queueName())
			.build();
	}
}

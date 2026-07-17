package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

public record InsertIndexer(
	String prefix,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerType type,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerStatus status,
	IndexerProvisioningState provisioningState,
	IndexerRuntimeState runtimeState,
	PublicationState publicationState,
	MutationState mutationState
) {
	public InsertIndexer {
		Objects.requireNonNull(prefix, "prefix");
	}

	public InsertIndexer(
		String prefix,
		Integer targetId,
		String targetName,
		String indexName,
		String queueName,
		IndexerType type,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		this(
			prefix,
			targetId,
			targetName,
			indexName,
			queueName,
			type,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY,
			runtimeState,
			publicationState,
			mutationState
		);
	}

	public InsertIndexer(
		String prefix,
		Integer targetId,
		String targetName,
		String indexName,
		String queueName,
		IndexerType type,
		IndexerRole role,
		IndexResourceOwnership indexOwnership,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		this(
			prefix,
			targetId,
			targetName,
			indexName,
			queueName,
			type,
			role,
			indexOwnership,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY,
			runtimeState,
			publicationState,
			mutationState
		);
	}
}

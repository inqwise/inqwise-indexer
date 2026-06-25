package com.inqwise.indexer.metadata;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;

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
			prefix == null ? "test" : prefix,
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
			prefix == null ? "test" : prefix,
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

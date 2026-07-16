package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;

public record CreateIndexerProvisioningRequest(
	String prefix,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerType indexerType,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerRuntimeState runtimeState,
	MutationState mutationState
) {
	public CreateIndexerProvisioningRequest {
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		indexName = Objects.requireNonNull(indexName, "indexName");
		indexerType = indexerType == null ? IndexerType.INDEX : indexerType;
		role = role == null ? IndexerRole.LIVE_WRITER : role;
		indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
		mutationState = mutationState == null ? MutationState.WRITABLE : mutationState;
	}
}

package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;

public record CreateIndexerProvisioningRequest(
	String prefix,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerRuntimeState runtimeState
) {
	public CreateIndexerProvisioningRequest {
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		indexName = Objects.requireNonNull(indexName, "indexName");
		role = role == null ? IndexerRole.LIVE_WRITER : role;
		indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
	}
}

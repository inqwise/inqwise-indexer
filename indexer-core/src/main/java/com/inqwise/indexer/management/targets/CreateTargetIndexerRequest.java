package com.inqwise.indexer.management.targets;

import java.util.Objects;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

public record CreateTargetIndexerRequest(
	String prefix,
	String indexName,
	String queueName,
	IndexerType indexerType,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerRuntimeState runtimeState,
	PublicationState publicationState,
	MutationState mutationState,
	InitialPublicationMode initialPublicationMode
) {
	public CreateTargetIndexerRequest {
		Objects.requireNonNull(indexName, "indexName");
		indexerType = indexerType == null ? IndexerType.INDEX : indexerType;
		role = role == null ? IndexerRole.LIVE_WRITER : role;
		indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
		publicationState = publicationState == null
			? PublicationState.UNPUBLISHED
			: publicationState;
		mutationState = mutationState == null ? MutationState.WRITABLE : mutationState;
		Objects.requireNonNull(initialPublicationMode, "initialPublicationMode");
	}
}

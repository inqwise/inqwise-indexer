package com.inqwise.indexer.metadata;

public record ReplacePublishedIndexer(
	Integer targetId,
	Integer candidateIndexerId,
	long expectedCandidateVersion,
	Integer previousIndexerId,
	Long expectedPreviousVersion,
	Integer ownershipSourceIndexerId,
	Long expectedOwnershipSourceVersion
) {
}

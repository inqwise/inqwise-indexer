package com.inqwise.indexer.load.repository;

public record AttachLiveWriterRequest(
	Integer indexerId,
	Integer liveIndexerId,
	long expectedVersion
) {
}

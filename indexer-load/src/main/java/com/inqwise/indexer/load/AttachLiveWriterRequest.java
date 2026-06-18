package com.inqwise.indexer.load;

public record AttachLiveWriterRequest(
	Integer indexerId,
	Integer liveIndexerId,
	long expectedVersion
) {
}

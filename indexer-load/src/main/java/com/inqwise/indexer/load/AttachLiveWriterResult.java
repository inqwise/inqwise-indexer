package com.inqwise.indexer.load;

public record AttachLiveWriterResult(
	boolean attached,
	Integer liveIndexerId,
	long version
) {
}

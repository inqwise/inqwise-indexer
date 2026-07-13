package com.inqwise.indexer.load.repository;

public record AttachLiveWriterResult(
	boolean attached,
	Integer liveIndexerId,
	long version
) {
}

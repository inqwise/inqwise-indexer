package com.inqwise.indexer.load;

public record LoadStopRequest(
	Integer indexerId,
	String reason
) {
}

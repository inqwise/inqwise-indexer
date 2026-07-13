package com.inqwise.indexer.load.api;

public record LoadStopRequest(
	Integer indexerId,
	String reason
) {
}

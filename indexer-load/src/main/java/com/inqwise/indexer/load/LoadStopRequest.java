package com.inqwise.indexer.load;

public record LoadStopRequest(
	Integer loadIndexerId,
	String reason
) {
}

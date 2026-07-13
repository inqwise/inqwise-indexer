package com.inqwise.indexer.load.catalog;

import com.inqwise.indexer.load.api.LoadRequest;

public record LoadStartContext(
	LoadRequest request,
	String indexName,
	String queueName
) {
}

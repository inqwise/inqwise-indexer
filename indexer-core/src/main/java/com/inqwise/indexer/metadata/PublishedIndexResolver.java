package com.inqwise.indexer.metadata;

import java.util.List;

import io.vertx.core.Future;

public interface PublishedIndexResolver {
	Future<List<PublishedIndex>> resolvePublishedIndexes(PublishedIndexQuery query);
}

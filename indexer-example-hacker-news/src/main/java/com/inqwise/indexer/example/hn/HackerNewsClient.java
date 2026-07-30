package com.inqwise.indexer.example.hn;

import java.util.Optional;

import io.vertx.core.Future;

public interface HackerNewsClient {
	Future<HackerNewsUpdates> fetchUpdates();

	Future<Optional<HackerNewsItem>> fetchItem(long id);
}

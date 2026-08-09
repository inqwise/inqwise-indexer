package com.inqwise.indexer.example.hn.reports;

import io.vertx.core.Future;

public interface HackerNewsReports {
	Future<HackerNewsStoriesResult> stories(HackerNewsStoriesRequest request);

	Future<HackerNewsAuthorSummaryResult> storyAuthors(
		HackerNewsAuthorSummaryRequest request
	);
}

package com.inqwise.indexer.example.hn.reports;

import java.util.Objects;

import com.inqwise.indexer.query.TypedReportExecutor;

import io.vertx.core.Future;

public final class DefaultHackerNewsReports implements HackerNewsReports {
	private final TypedReportExecutor reports;

	public DefaultHackerNewsReports(TypedReportExecutor reports) {
		this.reports = Objects.requireNonNull(reports, "reports");
	}

	@Override
	public Future<HackerNewsStoriesResult> stories(HackerNewsStoriesRequest request) {
		return reports.execute(HackerNewsReportBindings.stories(), request);
	}

	@Override
	public Future<HackerNewsAuthorSummaryResult> storyAuthors(
		HackerNewsAuthorSummaryRequest request
	) {
		return reports.execute(HackerNewsReportBindings.storyAuthors(), request);
	}
}

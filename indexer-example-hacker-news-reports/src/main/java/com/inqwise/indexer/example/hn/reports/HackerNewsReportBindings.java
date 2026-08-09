package com.inqwise.indexer.example.hn.reports;

import com.inqwise.indexer.query.DefaultTypedReport;
import com.inqwise.indexer.query.TypedReport;

public final class HackerNewsReportBindings {
	private static final TypedReport<HackerNewsStoriesRequest, HackerNewsStoriesResult> STORIES =
		DefaultTypedReport.<HackerNewsStoriesRequest, HackerNewsStoriesResult>builder()
			.withName(HackerNewsStoriesReportDefinition.REPORT_NAME)
			.withRequestCodec(new HackerNewsStoriesRequestCodec())
			.withResultCodec(new HackerNewsStoriesResultCodec())
			.build();
	private static final TypedReport<
		HackerNewsAuthorSummaryRequest,
		HackerNewsAuthorSummaryResult
	> STORY_AUTHORS = DefaultTypedReport
		.<HackerNewsAuthorSummaryRequest, HackerNewsAuthorSummaryResult>builder()
		.withName(HackerNewsAuthorSummaryReportDefinition.REPORT_NAME)
		.withRequestCodec(new HackerNewsAuthorSummaryRequestCodec())
		.withResultCodec(new HackerNewsAuthorSummaryResultCodec())
		.build();

	private HackerNewsReportBindings() {
	}

	public static TypedReport<HackerNewsStoriesRequest, HackerNewsStoriesResult> stories() {
		return STORIES;
	}

	public static TypedReport<
		HackerNewsAuthorSummaryRequest,
		HackerNewsAuthorSummaryResult
	> storyAuthors() {
		return STORY_AUTHORS;
	}
}

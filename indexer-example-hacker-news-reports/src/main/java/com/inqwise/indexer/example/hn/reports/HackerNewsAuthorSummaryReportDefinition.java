package com.inqwise.indexer.example.hn.reports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.inqwise.indexer.query.DocumentQueryGroupResult;
import com.inqwise.indexer.query.DocumentQueryResults;
import com.inqwise.indexer.query.ReportDefinition;
import com.inqwise.indexer.query.ReportDescriptor;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.ReportQueryPlan;
import com.inqwise.indexer.query.ReportQueryScope;
import com.inqwise.indexer.query.ReportRequestCodec;
import com.inqwise.indexer.query.ReportResultCodec;

public final class HackerNewsAuthorSummaryReportDefinition implements ReportDefinition<
	HackerNewsAuthorSummaryRequest,
	HackerNewsAuthorSummaryResult
> {
	public static final String REPORT_NAME = "hacker-news.story-authors";
	public static final int MAX_LIMIT = 100;

	private static final HackerNewsAuthorSummaryRequestCodec REQUEST_CODEC =
		new HackerNewsAuthorSummaryRequestCodec();
	private static final HackerNewsAuthorSummaryResultCodec RESULT_CODEC =
		new HackerNewsAuthorSummaryResultCodec();

	private final ReportDescriptor descriptor = ReportDescriptor.builder()
		.withName(REPORT_NAME)
		.withTargetName(HackerNewsReportConstants.TARGET_NAME)
		.withScope(ReportQueryScope.builder()
			.withMandatoryFilter(HackerNewsSourceFilter.INSTANCE)
			.withMaxLimit(MAX_LIMIT)
			.build())
		.withSupportedSchemas(java.util.Set.of(HackerNewsStoriesReportDefinition.SCHEMA))
		.build();

	@Override
	public ReportDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public ReportRequestCodec<HackerNewsAuthorSummaryRequest> requestCodec() {
		return REQUEST_CODEC;
	}

	@Override
	public ReportResultCodec<HackerNewsAuthorSummaryResult> resultCodec() {
		return RESULT_CODEC;
	}

	@Override
	public ReportQueryPlan plan(
		HackerNewsAuthorSummaryRequest request,
		ReportExecutionContext context
	) {
		return ReportQueryPlan.builder()
			.withFromInclusive(request.fromInclusive())
			.withToExclusive(request.toExclusive())
			.withFilter(HackerNewsStoryFilter.builder()
				.withMinimumScore(request.minimumScore())
				.build())
			.withLimit(request.limit())
			.withQuery(HackerNewsAuthorSummaryQuery.builder()
				.withOrderBy(request.orderBy())
				.build())
			.build();
	}

	@Override
	public HackerNewsAuthorSummaryResult decode(DocumentQueryResults results) {
		Map<String, HackerNewsAuthorSummary> authors = new LinkedHashMap<>();
		HackerNewsAuthorOrder order = null;
		for (DocumentQueryGroupResult group : results.groups()) {
			if (!(group.result() instanceof HackerNewsAuthorSummaryQueryResult queryResult)) {
				throw new IllegalArgumentException(
					"Unexpected query result for " + REPORT_NAME + ": "
						+ group.result().getClass().getName()
				);
			}
			if (order != null && order != queryResult.orderBy()) {
				throw new IllegalArgumentException("Inconsistent author-summary ordering");
			}
			order = queryResult.orderBy();
			queryResult.authors().forEach(author -> authors.merge(
				author.author(),
				author,
				this::merge
			));
		}

		if (order == null) {
			return HackerNewsAuthorSummaryResult.builder().withAuthors(List.of()).build();
		}
		return HackerNewsAuthorSummaryResult.builder()
			.withAuthors(authors.values().stream()
				.sorted(HackerNewsAuthorSummary.comparator(order))
				.limit(results.effectiveLimit())
				.toList())
			.build();
	}

	private HackerNewsAuthorSummary merge(
		HackerNewsAuthorSummary first,
		HackerNewsAuthorSummary second
	) {
		return HackerNewsAuthorSummary.builder()
			.withAuthor(first.author())
			.withStoryCount(Math.addExact(first.storyCount(), second.storyCount()))
			.withTotalScore(Math.addExact(first.totalScore(), second.totalScore()))
			.withMaxScore(Math.max(first.maxScore(), second.maxScore()))
			.withLatestStoryTime(first.latestStoryTime().isAfter(second.latestStoryTime())
				? first.latestStoryTime()
				: second.latestStoryTime())
			.build();
	}
}

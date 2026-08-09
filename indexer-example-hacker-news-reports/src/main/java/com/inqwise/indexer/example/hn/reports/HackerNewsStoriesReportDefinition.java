package com.inqwise.indexer.example.hn.reports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.inqwise.indexer.query.DocumentQueryGroupResult;
import com.inqwise.indexer.query.DocumentQueryResults;
import com.inqwise.indexer.query.IndexSchema;
import com.inqwise.indexer.query.ReportDefinition;
import com.inqwise.indexer.query.ReportDescriptor;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.ReportQueryPlan;
import com.inqwise.indexer.query.ReportQueryScope;
import com.inqwise.indexer.query.ReportRequestCodec;
import com.inqwise.indexer.query.ReportResultCodec;

public final class HackerNewsStoriesReportDefinition
	implements ReportDefinition<HackerNewsStoriesRequest, HackerNewsStoriesResult> {

	public static final String REPORT_NAME = "hacker-news.stories";
	public static final int MAX_LIMIT = 100;
	public static final IndexSchema SCHEMA = IndexSchema.builder()
		.withName("default")
		.withVersion("v1")
		.build();

	private static final HackerNewsStoriesRequestCodec REQUEST_CODEC =
		new HackerNewsStoriesRequestCodec();
	private static final HackerNewsStoriesResultCodec RESULT_CODEC =
		new HackerNewsStoriesResultCodec();
	private static final HackerNewsStoriesCursorCodec CURSOR_CODEC =
		new HackerNewsStoriesCursorCodec();

	private final ReportDescriptor descriptor = ReportDescriptor.builder()
		.withName(REPORT_NAME)
		.withTargetName(HackerNewsReportConstants.TARGET_NAME)
		.withScope(ReportQueryScope.builder()
			.withMandatoryFilter(HackerNewsSourceFilter.INSTANCE)
			.withMaxLimit(MAX_LIMIT)
			.build())
		.withSupportedSchemas(java.util.Set.of(SCHEMA))
		.build();

	@Override
	public ReportDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public ReportRequestCodec<HackerNewsStoriesRequest> requestCodec() {
		return REQUEST_CODEC;
	}

	@Override
	public ReportResultCodec<HackerNewsStoriesResult> resultCodec() {
		return RESULT_CODEC;
	}

	@Override
	public ReportQueryPlan plan(
		HackerNewsStoriesRequest request,
		ReportExecutionContext context
	) {
		HackerNewsStoriesCursor cursor = CURSOR_CODEC.decode(request.cursor(), request);
		return ReportQueryPlan.builder()
			.withFromInclusive(request.fromInclusive())
			.withToExclusive(request.toExclusive())
			.withFilter(HackerNewsStoryFilter.builder()
				.withMinimumScore(request.minimumScore())
				.build())
			.withLimit(request.limit())
			.withQuery(HackerNewsStoryQuery.builder()
				.withCursor(cursor)
				.withRequestFingerprint(CURSOR_CODEC.fingerprint(request))
				.build())
			.build();
	}

	@Override
	public HackerNewsStoriesResult decode(DocumentQueryResults results) {
		Map<Long, HackerNewsStorySummary> storiesById = new LinkedHashMap<>();
		boolean providerHasMore = false;
		String requestFingerprint = null;
		for (DocumentQueryGroupResult group : results.groups()) {
			if (!(group.result() instanceof HackerNewsStoryQueryResult queryResult)) {
				throw new IllegalArgumentException(
					"Unexpected query result for " + REPORT_NAME + ": "
						+ group.result().getClass().getName()
				);
			}
			providerHasMore |= queryResult.hasMore();
			if (requestFingerprint == null) {
				requestFingerprint = queryResult.requestFingerprint();
			} else if (!requestFingerprint.equals(queryResult.requestFingerprint())) {
				throw new IllegalArgumentException(
					"Story query groups returned incompatible cursor criteria"
				);
			}
			for (HackerNewsStorySummary story : queryResult.stories()) {
				storiesById.merge(story.id(), story, this::preferred);
			}
		}

		List<HackerNewsStorySummary> ordered = storiesById.values().stream()
			.sorted(HackerNewsStoryOrder.COMPARATOR)
			.toList();
		List<HackerNewsStorySummary> stories = ordered.stream()
			.limit(results.effectiveLimit())
			.toList();
		boolean hasMore = providerHasMore || ordered.size() > stories.size();
		String nextCursor = hasMore && !stories.isEmpty()
			? CURSOR_CODEC.encode(cursor(stories.getLast(), requestFingerprint))
			: null;
		return HackerNewsStoriesResult.builder()
			.withStories(stories)
			.withNextCursor(nextCursor)
			.build();
	}

	private HackerNewsStoriesCursor cursor(
		HackerNewsStorySummary story,
		String requestFingerprint
	) {
		return HackerNewsStoriesCursor.builder()
			.withScore(story.score())
			.withTime(story.time())
			.withId(story.id())
			.withRequestFingerprint(requestFingerprint)
			.build();
	}

	private HackerNewsStorySummary preferred(
		HackerNewsStorySummary existing,
		HackerNewsStorySummary candidate
	) {
		return candidate.time().isBefore(existing.time()) ? existing : candidate;
	}
}

package com.inqwise.indexer.example.hn.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexQuery;
import com.inqwise.indexer.query.AllOfQueryFilter;
import com.inqwise.indexer.query.ConsumerReportExecutionContextResolver;
import com.inqwise.indexer.query.DefaultReportsFacade;
import com.inqwise.indexer.query.DocumentQueryExecution;
import com.inqwise.indexer.query.QueryFilter;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.ReportQueryScope;
import com.inqwise.indexer.query.TypedReportExecutor;
import com.inqwise.indexer.query.service.ReportCaller;
import com.inqwise.indexer.query.service.ReportsService;
import com.inqwise.indexer.query.service.ReportsServiceImpl;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HackerNewsReportsTest {
	private static final Instant JANUARY_1 = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant JANUARY_2 = Instant.parse("2026-01-02T00:00:00Z");
	private static final Instant JANUARY_3 = Instant.parse("2026-01-03T00:00:00Z");
	private static final Instant JANUARY_4 = Instant.parse("2026-01-04T00:00:00Z");
	private static final Instant JANUARY_5 = Instant.parse("2026-01-05T00:00:00Z");

	@Test
	void typedFacadeEnforcesTrustedScopeAndDecodesStories(VertxTestContext testContext) {
		AtomicReference<PublishedIndexQuery> resolvedQuery = new AtomicReference<>();
		AtomicReference<DocumentQueryExecution> providerExecution = new AtomicReference<>();
		DefaultReportsFacade facade = new DefaultReportsFacade(
			HackerNewsReportCatalog.create(),
			query -> {
				resolvedQuery.set(query);
				return Future.succeededFuture(List.of(publishedIndex()));
			},
			execution -> {
				providerExecution.set(execution);
				HackerNewsStoryQuery query = assertInstanceOf(
					HackerNewsStoryQuery.class,
					execution.query()
				);
				return Future.succeededFuture(HackerNewsStoryQueryResult.builder()
					.withStories(List.of(
						story(1, 15, JANUARY_2),
						story(2, 40, JANUARY_3),
						story(3, 30, JANUARY_4)
					))
					.withRequestFingerprint(query.requestFingerprint())
					.build());
			}
		);
		ReportExecutionContext consumerScope = ReportExecutionContext.builder()
			.withScope(ReportQueryScope.builder()
				.withFromInclusive(JANUARY_2)
				.withToExclusive(JANUARY_4)
				.withMandatoryFilter(TrustedTenantFilter.INSTANCE)
				.withMaxLimit(2)
				.build())
			.build();
		ReportsService service = new ReportsServiceImpl(
			facade,
			new ConsumerReportExecutionContextResolver(Map.of(
				HackerNewsReportConstants.CONSUMER_NAME,
				consumerScope
			)),
			ReportCaller.builder()
				.withConsumerName(HackerNewsReportConstants.CONSUMER_NAME)
				.withSubject("bounded-user")
				.build()
		);
		HackerNewsReports reports = new DefaultHackerNewsReports(
			new TypedReportExecutor(service)
		);

		reports.stories(HackerNewsStoriesRequest.builder()
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_5)
			.withMinimumScore(10)
			.withLimit(50)
			.build())
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(List.of(2L, 3L), result.stories().stream()
					.map(HackerNewsStorySummary::id)
					.toList());
				assertEquals("hacker-news", resolvedQuery.get().targetName());
				assertEquals(JANUARY_2, resolvedQuery.get().fromInclusive());
				assertEquals(JANUARY_4, resolvedQuery.get().toExclusive());

				DocumentQueryExecution execution = providerExecution.get();
				assertEquals(2, execution.limit());
				assertEquals(HackerNewsStoryQuery.CAPABILITY, execution.query().capability());
				AllOfQueryFilter filter = assertInstanceOf(
					AllOfQueryFilter.class,
					execution.filter()
				);
				assertEquals(List.of(
					HackerNewsSourceFilter.INSTANCE,
					TrustedTenantFilter.INSTANCE,
					HackerNewsStoryFilter.builder().withMinimumScore(10).build()
				), filter.filters());
				testContext.completeNow();
			})));
	}

	@Test
	void authorSummaryUsesTrustedScopeAndRequestedOrdering(
		VertxTestContext testContext
	) {
		AtomicReference<DocumentQueryExecution> providerExecution = new AtomicReference<>();
		DefaultReportsFacade facade = new DefaultReportsFacade(
			HackerNewsReportCatalog.create(),
			ignored -> Future.succeededFuture(List.of(publishedIndex())),
			execution -> {
				providerExecution.set(execution);
				return Future.succeededFuture(HackerNewsAuthorSummaryQueryResult.builder()
					.withOrderBy(HackerNewsAuthorOrder.LATEST_STORY)
					.withAuthors(List.of(
						author("older", 2, 100, 70, JANUARY_2),
						author("newer", 1, 20, 20, JANUARY_3)
					))
					.build());
			}
		);
		ReportExecutionContext consumerScope = ReportExecutionContext.builder()
			.withScope(ReportQueryScope.builder()
				.withFromInclusive(JANUARY_2)
				.withToExclusive(JANUARY_4)
				.withMandatoryFilter(TrustedTenantFilter.INSTANCE)
				.withMaxLimit(1)
				.build())
			.build();
		ReportsService service = new ReportsServiceImpl(
			facade,
			new ConsumerReportExecutionContextResolver(Map.of(
				HackerNewsReportConstants.CONSUMER_NAME,
				consumerScope
			)),
			ReportCaller.builder()
				.withConsumerName(HackerNewsReportConstants.CONSUMER_NAME)
				.withSubject("bounded-user")
				.build()
		);
		HackerNewsReports reports = new DefaultHackerNewsReports(
			new TypedReportExecutor(service)
		);

		reports.storyAuthors(HackerNewsAuthorSummaryRequest.builder()
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_5)
			.withMinimumScore(10)
			.withLimit(50)
			.withOrderBy(HackerNewsAuthorOrder.LATEST_STORY)
			.build())
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(List.of("newer"), result.authors().stream()
					.map(HackerNewsAuthorSummary::author)
					.toList());
				DocumentQueryExecution execution = providerExecution.get();
				assertEquals(JANUARY_2, execution.fromInclusive());
				assertEquals(JANUARY_4, execution.toExclusive());
				assertEquals(1, execution.limit());
				HackerNewsAuthorSummaryQuery query = assertInstanceOf(
					HackerNewsAuthorSummaryQuery.class,
					execution.query()
				);
				assertEquals(HackerNewsAuthorOrder.LATEST_STORY, query.orderBy());
				testContext.completeNow();
			})));
	}

	@Test
	void codecsRoundTripTypedRequestAndResult() {
		HackerNewsStoriesRequestCodec requestCodec = new HackerNewsStoriesRequestCodec();
		HackerNewsStoriesRequest firstRequest = HackerNewsStoriesRequest.builder()
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_5)
			.withMinimumScore(20)
			.withLimit(10)
			.build();
		HackerNewsStoriesCursorCodec cursorCodec = new HackerNewsStoriesCursorCodec();
		String cursor = cursorCodec.encode(HackerNewsStoriesCursor.builder()
			.withScore(100)
			.withTime(JANUARY_3)
			.withId(42)
			.withRequestFingerprint(cursorCodec.fingerprint(firstRequest))
			.build());
		HackerNewsStoriesRequest request = HackerNewsStoriesRequest.builder()
			.withFromInclusive(firstRequest.fromInclusive())
			.withToExclusive(firstRequest.toExclusive())
			.withMinimumScore(firstRequest.minimumScore())
			.withLimit(firstRequest.limit())
			.withCursor(cursor)
			.build();
		assertEquals(request, requestCodec.decode(requestCodec.encode(request)));
		assertThrows(
			IllegalArgumentException.class,
			() -> requestCodec.decode(requestCodec.encode(HackerNewsStoriesRequest.builder()
				.withFromInclusive(JANUARY_1)
				.withToExclusive(JANUARY_5)
				.withMinimumScore(21)
				.withCursor(cursor)
				.build()))
		);

		HackerNewsStoriesResultCodec resultCodec = new HackerNewsStoriesResultCodec();
		HackerNewsStoriesResult result = HackerNewsStoriesResult.builder()
			.withStories(List.of(story(42, 100, JANUARY_3)))
			.withNextCursor(cursor)
			.build();
		assertEquals(result, resultCodec.decode(resultCodec.encode(result)));

		HackerNewsAuthorSummaryRequestCodec authorRequestCodec =
			new HackerNewsAuthorSummaryRequestCodec();
		HackerNewsAuthorSummaryRequest authorRequest = HackerNewsAuthorSummaryRequest
			.builder()
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_5)
			.withMinimumScore(30)
			.withLimit(5)
			.withOrderBy(HackerNewsAuthorOrder.LATEST_STORY)
			.build();
		assertEquals(
			authorRequest,
			authorRequestCodec.decode(authorRequestCodec.encode(authorRequest))
		);

		HackerNewsAuthorSummaryResultCodec authorResultCodec =
			new HackerNewsAuthorSummaryResultCodec();
		HackerNewsAuthorSummaryResult authorResult = HackerNewsAuthorSummaryResult.builder()
			.withAuthors(List.of(HackerNewsAuthorSummary.builder()
				.withAuthor("example")
				.withStoryCount(2)
				.withTotalScore(150)
				.withMaxScore(100)
				.withLatestStoryTime(JANUARY_3)
				.build()))
			.build();
		assertEquals(
			authorResult,
			authorResultCodec.decode(authorResultCodec.encode(authorResult))
		);
	}

	private PublishedIndex publishedIndex() {
		return PublishedIndex.builder()
			.withIndexerId(1)
			.withTargetId(2)
			.withIndexName("hacker-news")
			.withSchemaName(HackerNewsStoriesReportDefinition.SCHEMA.name())
			.withSchemaVersion(HackerNewsStoriesReportDefinition.SCHEMA.version())
			.build();
	}

	private HackerNewsStorySummary story(long id, int score, Instant time) {
		return HackerNewsStorySummary.builder()
			.withId(id)
			.withAuthor("author-" + id)
			.withTitle("Story " + id)
			.withUrl("https://example.test/" + id)
			.withTime(time)
			.withScore(score)
			.withDescendants(score / 2)
			.build();
	}

	private HackerNewsAuthorSummary author(
		String name,
		long storyCount,
		long totalScore,
		int maxScore,
		Instant latestStoryTime
	) {
		return HackerNewsAuthorSummary.builder()
			.withAuthor(name)
			.withStoryCount(storyCount)
			.withTotalScore(totalScore)
			.withMaxScore(maxScore)
			.withLatestStoryTime(latestStoryTime)
			.build();
	}

	private enum TrustedTenantFilter implements QueryFilter {
		INSTANCE
	}
}

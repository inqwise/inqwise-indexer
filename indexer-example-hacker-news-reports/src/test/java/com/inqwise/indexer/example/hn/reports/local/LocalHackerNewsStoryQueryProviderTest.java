package com.inqwise.indexer.example.hn.reports.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorOrder;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummary;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryQuery;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryQueryResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryRequest;
import com.inqwise.indexer.example.hn.reports.HackerNewsSourceFilter;
import com.inqwise.indexer.example.hn.reports.DefaultHackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportConstants;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesReportDefinition;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesRequest;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryFilter;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryQuery;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryQueryResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsStorySummary;
import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.query.DocumentQueryExecution;
import com.inqwise.indexer.query.QueryFilter;
import com.inqwise.indexer.query.QueryFilters;
import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.TypedReportExecutor;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LocalHackerNewsQueryProviderTest {
	private static final Instant JANUARY_1 = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant JANUARY_2 = Instant.parse("2026-01-02T00:00:00Z");
	private static final Instant JANUARY_3 = Instant.parse("2026-01-03T00:00:00Z");
	private static final Instant JANUARY_4 = Instant.parse("2026-01-04T00:00:00Z");

	@Test
	void readsMultipleIndexesAndAppliesEveryConstraint(VertxTestContext testContext) {
		InMemoryIndexerDocumentStore documents = new InMemoryIndexerDocumentStore();
		LocalHackerNewsQueryProvider provider =
			new LocalHackerNewsQueryProvider(documents);

		Future.all(
			documents.put("hn-a", "1", story(1, 50, JANUARY_2, "story", "hacker-news")),
			documents.put("hn-a", "2", story(2, 40, JANUARY_2, "story", "hacker-news")),
			documents.put("hn-a", "3", story(3, 90, JANUARY_2, "comment", "hacker-news")),
			documents.put("hn-b", "2", story(2, 45, JANUARY_2, "story", "hacker-news")),
			documents.put("hn-b", "4", story(4, 100, JANUARY_4, "story", "hacker-news")),
			documents.put("hn-b", "6", story(6, 10, JANUARY_3, "story", "hacker-news"))
		).compose(ignored -> provider.execute(execution(QueryFilters.allOf(
			HackerNewsSourceFilter.INSTANCE,
			HackerNewsStoryFilter.builder().withMinimumScore(20).build()
		))))
			.onComplete(testContext.succeeding(raw -> testContext.verify(() -> {
				HackerNewsStoryQueryResult result = assertInstanceOf(
					HackerNewsStoryQueryResult.class,
					raw
				);
				assertEquals(List.of(1L, 2L), result.stories().stream()
					.map(HackerNewsStorySummary::id)
					.toList());
				assertEquals(45, result.stories().get(1).score());
				testContext.completeNow();
			})));
	}

	@Test
	void deduplicatesBeforeGroupingAuthorsAcrossIndexes(
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore documents = new InMemoryIndexerDocumentStore();
		LocalHackerNewsQueryProvider provider = new LocalHackerNewsQueryProvider(documents);

		Future.all(
			documents.put(
				"hn-a",
				"1",
				story(1, 40, JANUARY_2, "story", "hacker-news", "alice")
			),
			documents.put(
				"hn-a",
				"2",
				story(2, 20, JANUARY_2, "story", "hacker-news", "alice")
			),
			documents.put(
				"hn-b",
				"1",
				story(1, 50, JANUARY_2, "story", "hacker-news", "alice")
			),
			documents.put(
				"hn-b",
				"3",
				story(3, 80, JANUARY_3, "story", "hacker-news", "bob")
			),
			documents.put(
				"hn-b",
				"4",
				story(4, 100, JANUARY_3, "story", "hacker-news", "")
			)
		).compose(ignored -> provider.execute(authorExecution(QueryFilters.allOf(
			HackerNewsSourceFilter.INSTANCE,
			HackerNewsStoryFilter.builder().withMinimumScore(10).build()
		))))
			.onComplete(testContext.succeeding(raw -> testContext.verify(() -> {
				HackerNewsAuthorSummaryQueryResult result = assertInstanceOf(
					HackerNewsAuthorSummaryQueryResult.class,
					raw
				);
				assertEquals(List.of("alice", "bob"), result.authors().stream()
					.map(HackerNewsAuthorSummary::author)
					.toList());
				HackerNewsAuthorSummary alice = result.authors().getFirst();
				assertEquals(2, alice.storyCount());
				assertEquals(70, alice.totalScore());
				assertEquals(50, alice.maxScore());
				assertEquals(JANUARY_2, alice.latestStoryTime());
				assertEquals(HackerNewsAuthorOrder.STORY_COUNT, result.orderBy());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsUnknownFilterInsteadOfDroppingTrustedScope() {
		LocalHackerNewsQueryProvider provider =
			new LocalHackerNewsQueryProvider(new InMemoryIndexerDocumentStore());

		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> provider.execute(execution(UnknownFilter.INSTANCE))
		);
		assertEquals(
			"Unsupported Hacker News query filter: " + UnknownFilter.class.getName(),
			error.getMessage()
		);
	}

	@Test
	void rejectsMalformedStoredDocument() {
		InMemoryIndexerDocumentStore documents = new InMemoryIndexerDocumentStore();
		LocalHackerNewsQueryProvider provider = new LocalHackerNewsQueryProvider(documents);
		documents.put("hn-a", "42", new JsonObject()
			.put("id", 42L)
			.put("type", "story")
			.put("title", "Missing time")
			.put("source", "hacker-news"));

		assertThrows(
			IllegalArgumentException.class,
			() -> provider.execute(execution(QueryFilters.all()))
		);
	}

	@Test
	void localFactoryExecutesTheTypedFacadeEndToEnd(VertxTestContext testContext) {
		InMemoryIndexerDocumentStore documents = new InMemoryIndexerDocumentStore();
		HackerNewsReports reports = new DefaultHackerNewsReports(new TypedReportExecutor(
			LocalHackerNewsReports.create(
				ignored -> Future.succeededFuture(List.of(index(1, "hn-a"))),
				documents,
				ReportExecutionContextResolver.UNBOUNDED
			)
		));

		documents.put(
			"hn-a",
			"42",
			story(42, 75, JANUARY_2, "story", "hacker-news")
		).compose(ignored -> reports.stories(HackerNewsStoriesRequest.builder()
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_4)
			.withMinimumScore(50)
			.withLimit(10)
			.build()))
			.compose(result -> {
				assertEquals(List.of(42L), result.stories().stream()
					.map(HackerNewsStorySummary::id)
					.toList());
				return reports.storyAuthors(HackerNewsAuthorSummaryRequest.builder()
					.withFromInclusive(JANUARY_1)
					.withToExclusive(JANUARY_4)
					.withMinimumScore(50)
					.withLimit(10)
					.withOrderBy(HackerNewsAuthorOrder.TOTAL_SCORE)
					.build());
			})
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(List.of("author-42"), result.authors().stream()
					.map(HackerNewsAuthorSummary::author)
					.toList());
				testContext.completeNow();
			})));
	}

	@Test
	void pagesStoriesWithStableCursorAcrossMultipleIndexes(
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore documents = new InMemoryIndexerDocumentStore();
		HackerNewsReports reports = new DefaultHackerNewsReports(new TypedReportExecutor(
			LocalHackerNewsReports.create(
				ignored -> Future.succeededFuture(List.of(
					index(1, "hn-a"),
					index(2, "hn-b")
				)),
				documents,
				ReportExecutionContextResolver.UNBOUNDED
			)
		));

		Future.all(
			documents.put("hn-a", "1", story(1, 100, JANUARY_3, "story", "hacker-news")),
			documents.put("hn-a", "2", story(2, 90, JANUARY_3, "story", "hacker-news")),
			documents.put("hn-b", "2", story(2, 95, JANUARY_3, "story", "hacker-news")),
			documents.put("hn-b", "3", story(3, 90, JANUARY_3, "story", "hacker-news")),
			documents.put("hn-b", "4", story(4, 80, JANUARY_2, "story", "hacker-news")),
			documents.put("hn-b", "5", story(5, 70, JANUARY_2, "story", "hacker-news"))
		).compose(ignored -> reports.stories(pageRequest(null)))
			.compose(first -> reports.stories(pageRequest(first.nextCursor()))
				.map(second -> List.of(first, second)))
			.compose(pages -> reports.stories(pageRequest(pages.getLast().nextCursor()))
				.map(third -> List.of(pages.getFirst(), pages.getLast(), third)))
			.onComplete(testContext.succeeding(pages -> testContext.verify(() -> {
				assertEquals(List.of(1L, 2L), ids(pages.get(0)));
				assertEquals(List.of(3L, 4L), ids(pages.get(1)));
				assertEquals(List.of(5L), ids(pages.get(2)));
				assertNull(pages.get(2).nextCursor());
				testContext.completeNow();
			})));
	}

	private HackerNewsStoriesRequest pageRequest(String cursor) {
		return HackerNewsStoriesRequest.builder()
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_4)
			.withMinimumScore(0)
			.withLimit(2)
			.withCursor(cursor)
			.build();
	}

	private List<Long> ids(HackerNewsStoriesResult result) {
		return result.stories().stream().map(HackerNewsStorySummary::id).toList();
	}

	private DocumentQueryExecution execution(QueryFilter filter) {
		return DocumentQueryExecution.builder()
			.withReportName(HackerNewsStoriesReportDefinition.REPORT_NAME)
			.withTargetName(HackerNewsReportConstants.TARGET_NAME)
			.withSchema(HackerNewsStoriesReportDefinition.SCHEMA)
			.withIndexes(List.of(index(1, "hn-a"), index(2, "hn-b")))
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_4)
			.withFilter(filter)
			.withLimit(2)
			.withQuery(HackerNewsStoryQuery.builder()
				.withRequestFingerprint("test-request")
				.build())
			.build();
	}

	private DocumentQueryExecution authorExecution(QueryFilter filter) {
		return DocumentQueryExecution.builder()
			.withReportName("hacker-news.story-authors")
			.withTargetName(HackerNewsReportConstants.TARGET_NAME)
			.withSchema(HackerNewsStoriesReportDefinition.SCHEMA)
			.withIndexes(List.of(index(1, "hn-a"), index(2, "hn-b")))
			.withFromInclusive(JANUARY_1)
			.withToExclusive(JANUARY_4)
			.withFilter(filter)
			.withLimit(2)
			.withQuery(HackerNewsAuthorSummaryQuery.builder()
				.withOrderBy(HackerNewsAuthorOrder.STORY_COUNT)
				.build())
			.build();
	}

	private PublishedIndex index(int id, String name) {
		return PublishedIndex.builder()
			.withIndexerId(id)
			.withTargetId(id)
			.withIndexName(name)
			.withSchemaName(HackerNewsStoriesReportDefinition.SCHEMA.name())
			.withSchemaVersion(HackerNewsStoriesReportDefinition.SCHEMA.version())
			.build();
	}

	private JsonObject story(
		long id,
		int score,
		Instant time,
		String type,
		String source
	) {
		return story(id, score, time, type, source, "author-" + id);
	}

	private JsonObject story(
		long id,
		int score,
		Instant time,
		String type,
		String source,
		String author
	) {
		return new JsonObject()
			.put("id", id)
			.put("type", type)
			.put("by", author)
			.put("time", time.getEpochSecond())
			.put("title", "Story " + id)
			.put("url", "https://example.test/" + id)
			.put("score", score)
			.put("descendants", score / 2)
			.put("source", source);
	}

	private enum UnknownFilter implements QueryFilter {
		INSTANCE
	}
}

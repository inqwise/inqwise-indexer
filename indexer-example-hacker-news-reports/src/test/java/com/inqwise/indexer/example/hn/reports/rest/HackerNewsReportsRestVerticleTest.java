package com.inqwise.indexer.example.hn.reports.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummary;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryResultCodec;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesResultCodec;
import com.inqwise.indexer.example.hn.reports.HackerNewsStorySummary;
import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportExecutionResult;
import com.inqwise.indexer.query.service.ReportsServiceVerticle;
import com.inqwise.indexer.query.service.ReportsServices;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HackerNewsReportsRestVerticleTest {
	@Test
	void queriesTypedReportOverHttp(Vertx vertx, VertxTestContext testContext) {
		String address = ReportsServices.address("rest-test");
		AtomicReference<ReportExecutionRequest> captured = new AtomicReference<>();
		HackerNewsReportsRestVerticle rest = new HackerNewsReportsRestVerticle(
			HackerNewsReportsRestOptions.builder()
				.withEnabled(true)
				.withPort(0)
				.withReportsAddress(address)
				.build()
		);

		vertx.deployVerticle(new ReportsServiceVerticle(request -> {
			captured.set(request);
			return Future.succeededFuture(ReportExecutionResult.builder()
				.withPayload(new HackerNewsStoriesResultCodec().encode(
					HackerNewsStoriesResult.builder()
						.withStories(List.of(story()))
						.withNextCursor("next-page")
						.build()
				))
				.build());
		}, address))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> post(vertx, rest.actualPort(), new JsonObject()
				.put("from_inclusive", "2026-01-01T00:00:00Z")
				.put("to_exclusive", "2026-01-03T00:00:00Z")
				.put("minimum_score", 20)
				.put("limit", 5)))
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(200, response.statusCode());
				JsonObject body = response.body().toJsonObject();
				assertEquals(42L, body.getJsonArray("stories")
					.getJsonObject(0).getLong("id"));
				assertEquals("next-page", body.getString("next_cursor"));
				assertEquals("hacker-news.stories", captured.get().getReportName());
				assertEquals(20, captured.get().getParameters().getInteger("minimum_score"));
				assertEquals(false, captured.get().toJson().containsKey("caller"));
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsInvalidRangeBeforeCallingReportsService(
		Vertx vertx,
		VertxTestContext testContext
	) {
		String address = ReportsServices.address("invalid-rest-test");
		HackerNewsReportsRestVerticle rest = new HackerNewsReportsRestVerticle(
			HackerNewsReportsRestOptions.builder()
				.withEnabled(true)
				.withPort(0)
				.withReportsAddress(address)
				.build()
		);

		vertx.deployVerticle(rest)
			.compose(ignored -> post(vertx, rest.actualPort(), new JsonObject()
				.put("from_inclusive", "2026-01-03T00:00:00Z")
				.put("to_exclusive", "2026-01-01T00:00:00Z")))
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(400, response.statusCode());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsMalformedCursorBeforeCallingReportsService(
		Vertx vertx,
		VertxTestContext testContext
	) {
		String address = ReportsServices.address("invalid-cursor-rest-test");
		HackerNewsReportsRestVerticle rest = new HackerNewsReportsRestVerticle(
			HackerNewsReportsRestOptions.builder()
				.withEnabled(true)
				.withPort(0)
				.withReportsAddress(address)
				.build()
		);

		vertx.deployVerticle(rest)
			.compose(ignored -> post(vertx, rest.actualPort(), new JsonObject()
				.put("from_inclusive", "2026-01-01T00:00:00Z")
				.put("to_exclusive", "2026-01-03T00:00:00Z")
				.put("cursor", "not-a-cursor")))
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(400, response.statusCode());
				testContext.completeNow();
			})));
	}

	@Test
	void queriesAuthorSummaryOverHttp(Vertx vertx, VertxTestContext testContext) {
		String address = ReportsServices.address("author-rest-test");
		AtomicReference<ReportExecutionRequest> captured = new AtomicReference<>();
		HackerNewsReportsRestVerticle rest = new HackerNewsReportsRestVerticle(
			HackerNewsReportsRestOptions.builder()
				.withEnabled(true)
				.withPort(0)
				.withReportsAddress(address)
				.build()
		);

		vertx.deployVerticle(new ReportsServiceVerticle(request -> {
			captured.set(request);
			return Future.succeededFuture(ReportExecutionResult.builder()
				.withPayload(new HackerNewsAuthorSummaryResultCodec().encode(
					HackerNewsAuthorSummaryResult.builder()
						.withAuthors(List.of(author()))
						.build()
				))
				.build());
		}, address))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> post(
				vertx,
				rest.actualPort(),
				"/reports/hacker-news/story-authors",
				new JsonObject()
					.put("from_inclusive", "2026-01-01T00:00:00Z")
					.put("to_exclusive", "2026-01-03T00:00:00Z")
					.put("minimum_score", 20)
					.put("limit", 5)
					.put("order_by", "story_count")
			))
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(200, response.statusCode());
				JsonObject first = response.body().toJsonObject()
					.getJsonArray("authors").getJsonObject(0);
				assertEquals("example", first.getString("author"));
				assertEquals(2L, first.getLong("story_count"));
				assertEquals("hacker-news.story-authors", captured.get().getReportName());
				assertEquals("story_count", captured.get().getParameters()
					.getString("order_by"));
				testContext.completeNow();
			})));
	}

	private Future<Response> post(
		Vertx vertx,
		int port,
		JsonObject body
	) {
		return post(vertx, port, "/reports/hacker-news/stories", body);
	}

	private Future<Response> post(
		Vertx vertx,
		int port,
		String path,
		JsonObject body
	) {
		return vertx.createHttpClient()
			.request(HttpMethod.POST, port, "127.0.0.1", path)
			.compose(request -> request
				.putHeader("content-type", "application/json")
				.send(Buffer.buffer(body.encode())))
			.compose(response -> response.body()
				.map(buffer -> new Response(response.statusCode(), buffer)));
	}

	private HackerNewsStorySummary story() {
		return HackerNewsStorySummary.builder()
			.withId(42)
			.withAuthor("example")
			.withTitle("REST story")
			.withUrl("https://example.test/story")
			.withTime(Instant.parse("2026-01-02T00:00:00Z"))
			.withScore(75)
			.withDescendants(10)
			.build();
	}

	private HackerNewsAuthorSummary author() {
		return HackerNewsAuthorSummary.builder()
			.withAuthor("example")
			.withStoryCount(2)
			.withTotalScore(125)
			.withMaxScore(75)
			.withLatestStoryTime(Instant.parse("2026-01-02T00:00:00Z"))
			.build();
	}

	private record Response(int statusCode, Buffer body) {
	}
}

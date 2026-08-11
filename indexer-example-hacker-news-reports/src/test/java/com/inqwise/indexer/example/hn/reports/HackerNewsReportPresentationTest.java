package com.inqwise.indexer.example.hn.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.core.json.JsonObject;

class HackerNewsReportPresentationTest {
	@Test
	void describesOnlyUserParametersAndEncodedResults() {
		ReportPresentation stories = new HackerNewsStoriesReportDefinition()
			.presentation();
		JsonObject storyParameters = stories.getParametersSchema()
			.getJsonObject("properties");
		JsonObject storyResults = stories.getResultSchema().getJsonObject("properties");

		assertEquals(HackerNewsStoriesReportDefinition.REPORT_NAME, stories.getName());
		assertEquals(
			Set.of("from_inclusive", "to_exclusive", "minimum_score", "limit", "cursor"),
			storyParameters.fieldNames()
		);
		assertEquals(Set.of("stories", "next_cursor"), storyResults.fieldNames());
		assertEquals(
			HackerNewsStoriesReportDefinition.MAX_LIMIT,
			storyParameters.getJsonObject("limit").getInteger("maximum")
		);
		assertFalse(stories.toJson().encode().contains("target"));
		assertFalse(stories.toJson().encode().contains("consumer"));
		assertFalse(stories.toJson().encode().contains("provider"));

		ReportPresentation authors = new HackerNewsAuthorSummaryReportDefinition()
			.presentation();
		JsonObject authorParameters = authors.getParametersSchema()
			.getJsonObject("properties");
		assertEquals(
			Set.of("from_inclusive", "to_exclusive", "minimum_score", "limit", "order_by"),
			authorParameters.fieldNames()
		);
		assertTrue(authorParameters.getJsonObject("order_by").containsKey("enum"));
		assertEquals(
			Set.of("authors"),
			authors.getResultSchema().getJsonObject("properties").fieldNames()
		);
	}
}

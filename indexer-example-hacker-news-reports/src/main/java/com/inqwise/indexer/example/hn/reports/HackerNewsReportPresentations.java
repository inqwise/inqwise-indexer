package com.inqwise.indexer.example.hn.reports;

import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

final class HackerNewsReportPresentations {
	private HackerNewsReportPresentations() {
	}

	static ReportPresentation stories() {
		return ReportPresentation.builder()
			.withName(HackerNewsStoriesReportDefinition.REPORT_NAME)
			.withTitle("Stories")
			.withDescription("Search indexed stories by time and minimum score.")
			.withParametersSchema(objectSchema()
				.put("properties", new JsonObject()
					.put("from_inclusive", instant("From", "Inclusive start time."))
					.put("to_exclusive", instant("To", "Exclusive end time."))
					.put("minimum_score", integer(
						"Minimum score",
						"Only return stories at or above this score.",
						0,
						0,
						null
					))
					.put("limit", integer(
						"Limit",
						"Maximum stories returned per page.",
						25,
						1,
						HackerNewsStoriesReportDefinition.MAX_LIMIT
					))
					.put("cursor", string(
						"Continuation cursor",
						"Opaque cursor returned by the previous compatible request."
					)))
				.put("required", new JsonArray()
					.add("from_inclusive")
					.add("to_exclusive"))
				.put("additionalProperties", false))
			.withResultSchema(objectSchema()
				.put("properties", new JsonObject()
					.put("stories", new JsonObject()
						.put("type", "array")
						.put("title", "Stories")
						.put("items", storySchema()))
					.put("next_cursor", string(
						"Next cursor",
						"Opaque continuation cursor when another page is available."
					)))
				.put("required", new JsonArray().add("stories"))
				.put("additionalProperties", false))
			.build();
	}

	static ReportPresentation authorSummary() {
		return ReportPresentation.builder()
			.withName(HackerNewsAuthorSummaryReportDefinition.REPORT_NAME)
			.withTitle("Story authors")
			.withDescription("Aggregate indexed stories by author.")
			.withParametersSchema(objectSchema()
				.put("properties", new JsonObject()
					.put("from_inclusive", instant("From", "Inclusive start time."))
					.put("to_exclusive", instant("To", "Exclusive end time."))
					.put("minimum_score", integer(
						"Minimum score",
						"Only aggregate stories at or above this score.",
						0,
						0,
						null
					))
					.put("limit", integer(
						"Limit",
						"Maximum authors returned.",
						25,
						1,
						HackerNewsAuthorSummaryReportDefinition.MAX_LIMIT
					))
					.put("order_by", new JsonObject()
						.put("type", "string")
						.put("title", "Order by")
						.put("description", "Deterministic aggregate ordering.")
						.put("default", HackerNewsAuthorOrder.TOTAL_SCORE.value())
						.put("enum", new JsonArray(java.util.Arrays.stream(
							HackerNewsAuthorOrder.values()
						).map(HackerNewsAuthorOrder::value).toList()))))
				.put("required", new JsonArray()
					.add("from_inclusive")
					.add("to_exclusive"))
				.put("additionalProperties", false))
			.withResultSchema(objectSchema()
				.put("properties", new JsonObject()
					.put("authors", new JsonObject()
						.put("type", "array")
						.put("title", "Authors")
						.put("items", authorSchema())))
				.put("required", new JsonArray().add("authors"))
				.put("additionalProperties", false))
			.build();
	}

	private static JsonObject objectSchema() {
		return new JsonObject()
			.put("$schema", ReportPresentation.JSON_SCHEMA_DIALECT)
			.put("type", "object");
	}

	private static JsonObject storySchema() {
		return new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("id", integer("ID", "Stable story identifier.", null, 1, null))
				.put("author", nullableString("Author", "Story author."))
				.put("title", string("Title", "Story title."))
				.put("url", nullableString("URL", "Story URL.")
					.put("format", "uri"))
				.put("time", instant("Time", "Story publication time."))
				.put("score", integer("Score", "Story score.", null, 0, null))
				.put("descendants", integer(
					"Comments",
					"Known descendant count.",
					null,
					0,
					null
				)))
			.put("required", new JsonArray()
				.add("id")
				.add("title")
				.add("time")
				.add("score")
				.add("descendants"))
			.put("additionalProperties", false);
	}

	private static JsonObject authorSchema() {
		return new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("author", string("Author", "Story author."))
				.put("story_count", integer("Stories", "Story count.", null, 1, null))
				.put("total_score", integer(
					"Total score",
					"Sum of story scores.",
					null,
					0,
					null
				))
				.put("max_score", integer(
					"Maximum score",
					"Highest story score.",
					null,
					0,
					null
				))
				.put("latest_story_time", instant(
					"Latest story",
					"Most recent story time."
				)))
			.put("required", new JsonArray()
				.add("author")
				.add("story_count")
				.add("total_score")
				.add("max_score")
				.add("latest_story_time"))
			.put("additionalProperties", false);
	}

	private static JsonObject instant(String title, String description) {
		return string(title, description).put("format", "date-time");
	}

	private static JsonObject string(String title, String description) {
		return new JsonObject()
			.put("type", "string")
			.put("title", title)
			.put("description", description);
	}

	private static JsonObject nullableString(String title, String description) {
		return string(title, description).put(
			"type",
			new JsonArray().add("string").add("null")
		);
	}

	private static JsonObject integer(
		String title,
		String description,
		Integer defaultValue,
		Integer minimum,
		Integer maximum
	) {
		JsonObject schema = new JsonObject()
			.put("type", "integer")
			.put("title", title)
			.put("description", description);
		if (defaultValue != null) {
			schema.put("default", defaultValue);
		}
		if (minimum != null) {
			schema.put("minimum", minimum);
		}
		if (maximum != null) {
			schema.put("maximum", maximum);
		}
		return schema;
	}
}

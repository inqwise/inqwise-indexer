package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import com.inqwise.indexer.query.ReportRequestCodec;

import io.vertx.core.json.JsonObject;

public final class HackerNewsStoriesRequestCodec
	implements ReportRequestCodec<HackerNewsStoriesRequest> {
	private static final HackerNewsStoriesCursorCodec CURSOR_CODEC =
		new HackerNewsStoriesCursorCodec();

	@Override
	public HackerNewsStoriesRequest decode(JsonObject parameters) {
		Objects.requireNonNull(parameters, "parameters");
		HackerNewsStoriesRequest request = HackerNewsStoriesRequest.builder()
			.withFromInclusive(parseInstant(parameters, "from_inclusive"))
			.withToExclusive(parseInstant(parameters, "to_exclusive"))
			.withMinimumScore(HackerNewsReportJsonValues.optionalInteger(
				parameters,
				"minimum_score",
				0
			))
			.withLimit(HackerNewsReportJsonValues.optionalInteger(parameters, "limit", 25))
			.withCursor(HackerNewsReportJsonValues.optionalString(
				parameters,
				"cursor",
				null
			))
			.build();
		CURSOR_CODEC.decode(request.cursor(), request);
		return request;
	}

	@Override
	public JsonObject encode(HackerNewsStoriesRequest request) {
		Objects.requireNonNull(request, "request");
		JsonObject parameters = new JsonObject()
			.put("from_inclusive", request.fromInclusive().toString())
			.put("to_exclusive", request.toExclusive().toString())
			.put("minimum_score", request.minimumScore())
			.put("limit", request.limit());
		if (request.cursor() != null) {
			parameters.put("cursor", request.cursor());
		}
		return parameters;
	}

	private Instant parseInstant(JsonObject parameters, String field) {
		String value = HackerNewsReportJsonValues.requiredString(parameters, field);
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException error) {
			throw new IllegalArgumentException(field + " must be an ISO-8601 instant", error);
		}
	}
}

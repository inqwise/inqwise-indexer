package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import com.inqwise.indexer.query.ReportRequestCodec;

import io.vertx.core.json.JsonObject;

public final class HackerNewsAuthorSummaryRequestCodec
	implements ReportRequestCodec<HackerNewsAuthorSummaryRequest> {

	@Override
	public HackerNewsAuthorSummaryRequest decode(JsonObject parameters) {
		Objects.requireNonNull(parameters, "parameters");
		return HackerNewsAuthorSummaryRequest.builder()
			.withFromInclusive(parseInstant(parameters, "from_inclusive"))
			.withToExclusive(parseInstant(parameters, "to_exclusive"))
			.withMinimumScore(HackerNewsReportJsonValues.optionalInteger(
				parameters,
				"minimum_score",
				0
			))
			.withLimit(HackerNewsReportJsonValues.optionalInteger(parameters, "limit", 25))
			.withOrderBy(HackerNewsAuthorOrder.parse(
				HackerNewsReportJsonValues.optionalString(
					parameters,
					"order_by",
					HackerNewsAuthorOrder.TOTAL_SCORE.value()
				)
			))
			.build();
	}

	@Override
	public JsonObject encode(HackerNewsAuthorSummaryRequest request) {
		Objects.requireNonNull(request, "request");
		return new JsonObject()
			.put("from_inclusive", request.fromInclusive().toString())
			.put("to_exclusive", request.toExclusive().toString())
			.put("minimum_score", request.minimumScore())
			.put("limit", request.limit())
			.put("order_by", request.orderBy().value());
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

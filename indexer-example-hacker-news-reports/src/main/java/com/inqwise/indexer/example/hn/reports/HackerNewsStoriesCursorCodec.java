package com.inqwise.indexer.example.hn.reports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public final class HackerNewsStoriesCursorCodec {
	private static final int VERSION = 1;
	private static final int MAX_TOKEN_LENGTH = 2_048;

	public String encode(HackerNewsStoriesCursor cursor) {
		JsonObject value = new JsonObject()
			.put("v", VERSION)
			.put("score", cursor.score())
			.put("time", cursor.time().toString())
			.put("id", cursor.id())
			.put("request", cursor.requestFingerprint());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(
			value.encode().getBytes(StandardCharsets.UTF_8)
		);
	}

	public HackerNewsStoriesCursor decode(
		String token,
		HackerNewsStoriesRequest request
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_TOKEN_LENGTH) {
			throw new IllegalArgumentException("cursor is too long");
		}
		JsonObject value;
		try {
			value = new JsonObject(new String(
				Base64.getUrlDecoder().decode(token),
				StandardCharsets.UTF_8
			));
		} catch (IllegalArgumentException | DecodeException error) {
			throw new IllegalArgumentException("cursor is invalid", error);
		}
		try {
			int version = HackerNewsReportJsonValues.optionalInteger(value, "v", -1);
			if (version != VERSION) {
				throw new IllegalArgumentException("cursor version is unsupported");
			}
			HackerNewsStoriesCursor cursor = HackerNewsStoriesCursor.builder()
				.withScore(HackerNewsReportJsonValues.optionalInteger(value, "score", -1))
				.withTime(Instant.parse(HackerNewsReportJsonValues.requiredString(value, "time")))
				.withId(HackerNewsReportJsonValues.requiredLong(value, "id"))
				.withRequestFingerprint(HackerNewsReportJsonValues.requiredString(
					value,
					"request"
				))
				.build();
			if (!fingerprint(request).equals(cursor.requestFingerprint())) {
				throw new IllegalArgumentException(
					"cursor does not match the requested story criteria"
				);
			}
			return cursor;
		} catch (ClassCastException | DateTimeParseException | NullPointerException error) {
			throw new IllegalArgumentException("cursor is invalid", error);
		}
	}

	public String fingerprint(HackerNewsStoriesRequest request) {
		String criteria = String.join(
			"\n",
			request.fromInclusive().toString(),
			request.toExclusive().toString(),
			Integer.toString(request.minimumScore())
		);
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
				criteria.getBytes(StandardCharsets.UTF_8)
			));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

}

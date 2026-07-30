package com.inqwise.indexer.documents;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record DocumentIndexHit(
	String uid,
	double score,
	JsonObject document
) {
	public DocumentIndexHit {
		if (uid == null || uid.isBlank()) {
			throw new IllegalArgumentException("uid must not be blank");
		}
		if (!Double.isFinite(score) || score < 0.0d) {
			throw new IllegalArgumentException("score must be finite and non-negative");
		}
		document = Objects.requireNonNull(document, "document").copy();
	}

	@Override
	public JsonObject document() {
		return document.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String uid;
		private double score;
		private JsonObject document;

		private Builder() {
		}

		public Builder withUid(String value) {
			uid = value;
			return this;
		}

		public Builder withScore(double value) {
			score = value;
			return this;
		}

		public Builder withDocument(JsonObject value) {
			document = value == null ? null : value.copy();
			return this;
		}

		public DocumentIndexHit build() {
			return new DocumentIndexHit(uid, score, document);
		}
	}
}

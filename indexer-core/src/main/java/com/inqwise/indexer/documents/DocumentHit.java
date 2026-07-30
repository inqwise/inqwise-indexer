package com.inqwise.indexer.documents;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record DocumentHit(
	Integer indexerId,
	Integer targetId,
	String uid,
	double score,
	JsonObject document
) {
	public DocumentHit {
		Objects.requireNonNull(indexerId, "indexerId");
		Objects.requireNonNull(targetId, "targetId");
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
		private Integer indexerId;
		private Integer targetId;
		private String uid;
		private double score;
		private JsonObject document;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
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

		public DocumentHit build() {
			return new DocumentHit(indexerId, targetId, uid, score, document);
		}
	}
}

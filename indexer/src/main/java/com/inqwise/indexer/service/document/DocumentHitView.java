package com.inqwise.indexer.service.document;

import java.util.Objects;

import com.inqwise.indexer.documents.DocumentHit;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class DocumentHitView {
	private Integer indexerId;
	private Integer targetId;
	private String uid;
	private double score;
	private JsonObject document;

	public DocumentHitView() {
	}

	public DocumentHitView(JsonObject json) {
		indexerId = json.getInteger("indexer_id");
		targetId = json.getInteger("target_id");
		uid = json.getString("uid");
		score = json.getDouble("score", 0.0d);
		JsonObject value = json.getJsonObject("document");
		document = value == null ? null : value.copy();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("target_id", targetId)
			.put("uid", uid)
			.put("score", score)
			.put("document", document == null ? null : document.copy());
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public DocumentHitView setIndexerId(Integer value) {
		indexerId = value;
		return this;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public DocumentHitView setTargetId(Integer value) {
		targetId = value;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public DocumentHitView setUid(String value) {
		uid = value;
		return this;
	}

	public double getScore() {
		return score;
	}

	public DocumentHitView setScore(double value) {
		score = value;
		return this;
	}

	public JsonObject getDocument() {
		return document == null ? null : document.copy();
	}

	public DocumentHitView setDocument(JsonObject value) {
		document = value == null ? null : value.copy();
		return this;
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

		public Builder withHit(DocumentHit value) {
			Objects.requireNonNull(value, "value");
			indexerId = value.indexerId();
			targetId = value.targetId();
			uid = value.uid();
			score = value.score();
			document = value.document();
			return this;
		}

		public DocumentHitView build() {
			Objects.requireNonNull(indexerId, "indexerId");
			Objects.requireNonNull(targetId, "targetId");
			if (uid == null || uid.isBlank()) {
				throw new IllegalArgumentException("uid must not be blank");
			}
			Objects.requireNonNull(document, "document");
			return new DocumentHitView()
				.setIndexerId(indexerId)
				.setTargetId(targetId)
				.setUid(uid)
				.setScore(score)
				.setDocument(document);
		}
	}
}

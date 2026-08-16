package com.inqwise.indexer.query.service;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class ReportExecutionResult {
	private JsonObject payload = new JsonObject();

	public ReportExecutionResult() {
	}

	public ReportExecutionResult(JsonObject json) {
		JsonObject result = Objects.requireNonNull(json, "json");
		setPayload(result.getJsonObject("payload"));
	}

	public JsonObject toJson() {
		return new JsonObject().put("payload", payload.copy());
	}

	public JsonObject getPayload() {
		return payload.copy();
	}

	public ReportExecutionResult setPayload(JsonObject value) {
		payload = Objects.requireNonNull(value, "payload").copy();
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private JsonObject payload;

		private Builder() {
		}

		public Builder withPayload(JsonObject value) {
			payload = value == null ? null : value.copy();
			return this;
		}

		public ReportExecutionResult build() {
			return new ReportExecutionResult()
				.setPayload(Objects.requireNonNull(payload, "payload"));
		}
	}
}

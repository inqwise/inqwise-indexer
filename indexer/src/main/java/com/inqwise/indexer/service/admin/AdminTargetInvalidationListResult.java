package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetInvalidationListResult {
	public static final class Keys {
		public static final String TARGET_INVALIDATIONS = "target_invalidations";
		public static final String TRUNCATED = "truncated";

		private Keys() {
		}
	}

	private List<AdminTargetInvalidationView> targetInvalidations = List.of();
	private boolean truncated;

	public AdminTargetInvalidationListResult() {
	}

	public AdminTargetInvalidationListResult(JsonObject json) {
		this.targetInvalidations = json.getJsonArray(
			Keys.TARGET_INVALIDATIONS,
			new JsonArray()
		).stream()
			.map(JsonObject.class::cast)
			.map(AdminTargetInvalidationView::new)
			.toList();
		this.truncated = json.getBoolean(Keys.TRUNCATED, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.TARGET_INVALIDATIONS, new JsonArray(targetInvalidations.stream()
				.map(AdminTargetInvalidationView::toJson)
				.toList()))
			.put(Keys.TRUNCATED, truncated);
	}

	public static final class Builder {
		private List<AdminTargetInvalidationView> targetInvalidations = List.of();
		private boolean truncated;

		private Builder() {
		}

		public Builder withTargetInvalidations(List<AdminTargetInvalidationView> value) {
			targetInvalidations = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public Builder withTruncated(boolean value) {
			truncated = value;
			return this;
		}

		public AdminTargetInvalidationListResult build() {
			AdminTargetInvalidationListResult result =
				new AdminTargetInvalidationListResult();
			result.targetInvalidations = targetInvalidations;
			result.truncated = truncated;
			return result;
		}
	}
}

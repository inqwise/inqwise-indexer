package com.inqwise.indexer.service.admin;

import java.util.List;
import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminHotTargetListResult {
	public static final class Keys {
		public static final String HOT_TARGETS = "hot_targets";
		public static final String TRUNCATED = "truncated";

		private Keys() {
		}
	}

	private List<AdminHotTargetView> hotTargets = List.of();
	private boolean truncated;

	public AdminHotTargetListResult() {
	}

	public AdminHotTargetListResult(JsonObject json) {
		hotTargets = json.getJsonArray(Keys.HOT_TARGETS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminHotTargetView::new)
			.toList();
		truncated = json.getBoolean(Keys.TRUNCATED, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.HOT_TARGETS, new JsonArray(hotTargets.stream()
				.map(AdminHotTargetView::toJson)
				.toList()))
			.put(Keys.TRUNCATED, truncated);
	}

	public static final class Builder {
		private List<AdminHotTargetView> hotTargets;
		private boolean truncated;

		private Builder() {
		}

		public Builder withHotTargets(List<AdminHotTargetView> value) {
			hotTargets = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withTruncated(boolean value) {
			truncated = value;
			return this;
		}

		public AdminHotTargetListResult build() {
			AdminHotTargetListResult result = new AdminHotTargetListResult();
			result.hotTargets = List.copyOf(Objects.requireNonNull(
				hotTargets,
				"hotTargets"
			));
			result.truncated = truncated;
			return result;
		}
	}
}

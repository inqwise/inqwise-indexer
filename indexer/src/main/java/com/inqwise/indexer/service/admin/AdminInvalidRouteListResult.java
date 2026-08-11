package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminInvalidRouteListResult {
	public static final class Keys {
		public static final String INVALID_ROUTES = "invalid_routes";
		public static final String TRUNCATED = "truncated";

		private Keys() {
		}
	}

	private List<AdminInvalidRouteView> invalidRoutes = List.of();
	private boolean truncated;

	public AdminInvalidRouteListResult() {
	}

	public AdminInvalidRouteListResult(JsonObject json) {
		this.invalidRoutes = json.getJsonArray(Keys.INVALID_ROUTES, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminInvalidRouteView::new)
			.toList();
		this.truncated = json.getBoolean(Keys.TRUNCATED, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.INVALID_ROUTES, new JsonArray(invalidRoutes.stream()
				.map(AdminInvalidRouteView::toJson)
				.toList()))
			.put(Keys.TRUNCATED, truncated);
	}

	public static final class Builder {
		private List<AdminInvalidRouteView> invalidRoutes = List.of();
		private boolean truncated;

		private Builder() {
		}

		public Builder withInvalidRoutes(List<AdminInvalidRouteView> value) {
			invalidRoutes = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public Builder withTruncated(boolean value) {
			truncated = value;
			return this;
		}

		public AdminInvalidRouteListResult build() {
			AdminInvalidRouteListResult result = new AdminInvalidRouteListResult();
			result.invalidRoutes = invalidRoutes;
			result.truncated = truncated;
			return result;
		}
	}
}

package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminInfrastructureStatusResult {
	public static final class Keys {
		public static final String ITEMS = "items";

		private Keys() {
		}
	}

	private List<AdminInfrastructureItemView> items = List.of();

	public AdminInfrastructureStatusResult() {
	}

	public AdminInfrastructureStatusResult(JsonObject json) {
		this.items = json.getJsonArray(Keys.ITEMS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminInfrastructureItemView::new)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.ITEMS, new JsonArray(items.stream()
				.map(AdminInfrastructureItemView::toJson)
				.toList()));
	}

	public static final class Builder {
		private List<AdminInfrastructureItemView> items = List.of();

		private Builder() {
		}

		public Builder withItems(List<AdminInfrastructureItemView> value) {
			items = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public AdminInfrastructureStatusResult build() {
			AdminInfrastructureStatusResult result = new AdminInfrastructureStatusResult();
			result.items = items;
			return result;
		}
	}
}

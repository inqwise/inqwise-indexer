package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetDefinitionListResult {
	public static final class Keys {
		public static final String TARGET_DEFINITIONS = "target_definitions";

		private Keys() {
		}
	}

	private List<AdminTargetDefinitionView> targetDefinitions = List.of();

	public AdminTargetDefinitionListResult() {
	}

	public AdminTargetDefinitionListResult(JsonObject json) {
		this.targetDefinitions = json.getJsonArray(Keys.TARGET_DEFINITIONS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminTargetDefinitionView::new)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject().put(
			Keys.TARGET_DEFINITIONS,
			new JsonArray(targetDefinitions.stream()
				.map(AdminTargetDefinitionView::toJson)
				.toList())
		);
	}

	public List<AdminTargetDefinitionView> getTargetDefinitions() {
		return targetDefinitions;
	}

	public AdminTargetDefinitionListResult setTargetDefinitions(
		List<AdminTargetDefinitionView> targetDefinitions
	) {
		this.targetDefinitions = targetDefinitions == null ? List.of() : List.copyOf(targetDefinitions);
		return this;
	}

	public static final class Builder {
		private List<AdminTargetDefinitionView> targetDefinitions = List.of();

		private Builder() {
		}

		public Builder withTargetDefinitions(List<AdminTargetDefinitionView> value) {
			targetDefinitions = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public AdminTargetDefinitionListResult build() {
			return new AdminTargetDefinitionListResult()
				.setTargetDefinitions(targetDefinitions);
		}
	}
}

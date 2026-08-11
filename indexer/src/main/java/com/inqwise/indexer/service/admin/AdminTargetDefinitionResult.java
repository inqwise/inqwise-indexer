package com.inqwise.indexer.service.admin;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetDefinitionResult {
	public static final class Keys {
		public static final String TARGET_DEFINITION = "target_definition";

		private Keys() {
		}
	}

	private AdminTargetDefinitionView targetDefinition;

	public AdminTargetDefinitionResult() {
	}

	public AdminTargetDefinitionResult(JsonObject json) {
		JsonObject value = json.getJsonObject(Keys.TARGET_DEFINITION);
		this.targetDefinition = value == null ? null : new AdminTargetDefinitionView(value);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject().put(
			Keys.TARGET_DEFINITION,
			targetDefinition == null ? null : targetDefinition.toJson()
		);
	}

	public AdminTargetDefinitionView getTargetDefinition() {
		return targetDefinition;
	}

	public AdminTargetDefinitionResult setTargetDefinition(AdminTargetDefinitionView targetDefinition) {
		this.targetDefinition = targetDefinition;
		return this;
	}

	public static final class Builder {
		private AdminTargetDefinitionView targetDefinition;

		private Builder() {
		}

		public Builder withTargetDefinition(AdminTargetDefinitionView value) {
			targetDefinition = value;
			return this;
		}

		public AdminTargetDefinitionResult build() {
			return new AdminTargetDefinitionResult()
				.setTargetDefinition(Objects.requireNonNull(
					targetDefinition,
					"targetDefinition"
				));
		}
	}
}

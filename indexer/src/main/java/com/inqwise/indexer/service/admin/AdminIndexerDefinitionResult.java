package com.inqwise.indexer.service.admin;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerDefinitionResult {
	public static final class Keys {
		public static final String INDEXER_DEFINITION = "indexer_definition";

		private Keys() {
		}
	}

	private AdminIndexerDefinitionView indexerDefinition;

	public AdminIndexerDefinitionResult() {
	}

	public AdminIndexerDefinitionResult(JsonObject json) {
		JsonObject value = json.getJsonObject(Keys.INDEXER_DEFINITION);
		this.indexerDefinition = value == null ? null : new AdminIndexerDefinitionView(value);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject().put(
			Keys.INDEXER_DEFINITION,
			indexerDefinition == null ? null : indexerDefinition.toJson()
		);
	}

	public AdminIndexerDefinitionView getIndexerDefinition() {
		return indexerDefinition;
	}

	public AdminIndexerDefinitionResult setIndexerDefinition(
		AdminIndexerDefinitionView indexerDefinition
	) {
		this.indexerDefinition = indexerDefinition;
		return this;
	}

	public static final class Builder {
		private AdminIndexerDefinitionView indexerDefinition;

		private Builder() {
		}

		public Builder withIndexerDefinition(AdminIndexerDefinitionView value) {
			indexerDefinition = value;
			return this;
		}

		public AdminIndexerDefinitionResult build() {
			return new AdminIndexerDefinitionResult()
				.setIndexerDefinition(Objects.requireNonNull(
					indexerDefinition,
					"indexerDefinition"
				));
		}
	}
}

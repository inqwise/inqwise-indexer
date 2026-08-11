package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerDefinitionListResult {
	public static final class Keys {
		public static final String INDEXER_DEFINITIONS = "indexer_definitions";

		private Keys() {
		}
	}

	private List<AdminIndexerDefinitionView> indexerDefinitions = List.of();

	public AdminIndexerDefinitionListResult() {
	}

	public AdminIndexerDefinitionListResult(JsonObject json) {
		this.indexerDefinitions = json.getJsonArray(Keys.INDEXER_DEFINITIONS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminIndexerDefinitionView::new)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject().put(
			Keys.INDEXER_DEFINITIONS,
			new JsonArray(indexerDefinitions.stream()
				.map(AdminIndexerDefinitionView::toJson)
				.toList())
		);
	}

	public List<AdminIndexerDefinitionView> getIndexerDefinitions() {
		return indexerDefinitions;
	}

	public AdminIndexerDefinitionListResult setIndexerDefinitions(
		List<AdminIndexerDefinitionView> indexerDefinitions
	) {
		this.indexerDefinitions = indexerDefinitions == null
			? List.of()
			: List.copyOf(indexerDefinitions);
		return this;
	}

	public static final class Builder {
		private List<AdminIndexerDefinitionView> indexerDefinitions = List.of();

		private Builder() {
		}

		public Builder withIndexerDefinitions(List<AdminIndexerDefinitionView> value) {
			indexerDefinitions = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public AdminIndexerDefinitionListResult build() {
			return new AdminIndexerDefinitionListResult()
				.setIndexerDefinitions(indexerDefinitions);
		}
	}
}

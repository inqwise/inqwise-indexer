package com.inqwise.indexer.query.presentation;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public final class ReportPresentation {
	private static final int MAX_SCHEMA_CHARACTERS = 65_536;
	public static final String JSON_SCHEMA_DIALECT =
		"https://json-schema.org/draft/2020-12/schema";

	private String name;
	private String title;
	private String description = "";
	private JsonObject parametersSchema = new JsonObject();
	private JsonObject resultSchema = new JsonObject();

	public ReportPresentation() {
	}

	public ReportPresentation(JsonObject json) {
		JsonObject presentation = Objects.requireNonNull(json, "json");
		setName(presentation.getString("name"));
		setTitle(presentation.getString("title"));
		setDescription(presentation.getString("description", ""));
		setParametersSchema(presentation.getJsonObject("parameters_schema"));
		setResultSchema(presentation.getJsonObject("result_schema"));
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("name", name)
			.put("title", title)
			.put("description", description)
			.put("parameters_schema", parametersSchema.copy())
			.put("result_schema", resultSchema.copy());
	}

	public String getName() {
		return name;
	}

	public ReportPresentation setName(String value) {
		name = requireName(value);
		return this;
	}

	public String getTitle() {
		return title;
	}

	public ReportPresentation setTitle(String value) {
		title = requireText(value, "title");
		return this;
	}

	public String getDescription() {
		return description;
	}

	public ReportPresentation setDescription(String value) {
		description = value == null ? "" : value.trim();
		return this;
	}

	public JsonObject getParametersSchema() {
		return parametersSchema.copy();
	}

	public ReportPresentation setParametersSchema(JsonObject value) {
		parametersSchema = requireObjectSchema(value, "parametersSchema");
		return this;
	}

	public JsonObject getResultSchema() {
		return resultSchema.copy();
	}

	public ReportPresentation setResultSchema(JsonObject value) {
		resultSchema = requireObjectSchema(value, "resultSchema");
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}

	private static String requireName(String value) {
		String name = requireText(value, "name");
		if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
			throw new IllegalArgumentException("name is not HTTP-path compatible");
		}
		return name;
	}

	private static JsonObject requireObjectSchema(JsonObject value, String name) {
		JsonObject schema = Objects.requireNonNull(value, name).copy();
		if (!JSON_SCHEMA_DIALECT.equals(schema.getString("$schema"))) {
			throw new IllegalArgumentException(
				name + " must use JSON Schema draft 2020-12"
			);
		}
		if (!"object".equals(schema.getString("type"))) {
			throw new IllegalArgumentException(name + " must describe an object");
		}
		if (schema.encode().length() > MAX_SCHEMA_CHARACTERS) {
			throw new IllegalArgumentException(name + " is too large");
		}
		return schema;
	}

	public static final class Builder {
		private String name;
		private String title;
		private String description = "";
		private JsonObject parametersSchema;
		private JsonObject resultSchema;

		private Builder() {
		}

		public Builder withName(String value) {
			name = value;
			return this;
		}

		public Builder withTitle(String value) {
			title = value;
			return this;
		}

		public Builder withDescription(String value) {
			description = value;
			return this;
		}

		public Builder withParametersSchema(JsonObject value) {
			parametersSchema = value == null ? null : value.copy();
			return this;
		}

		public Builder withResultSchema(JsonObject value) {
			resultSchema = value == null ? null : value.copy();
			return this;
		}

		public ReportPresentation build() {
			return new ReportPresentation()
				.setName(name)
				.setTitle(title)
				.setDescription(description)
				.setParametersSchema(parametersSchema)
				.setResultSchema(resultSchema);
		}
	}
}

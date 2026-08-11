package com.inqwise.indexer.service.admin;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminInfrastructureItemView {
	public static final class Keys {
		public static final String NAME = "name";
		public static final String CATEGORY = "category";
		public static final String IMPLEMENTATION = "implementation";
		public static final String DETAILS = "details";

		private Keys() {
		}
	}

	private String name;
	private String category;
	private String implementation;
	private JsonObject details = new JsonObject();

	public AdminInfrastructureItemView() {
	}

	public AdminInfrastructureItemView(JsonObject json) {
		this.name = json.getString(Keys.NAME);
		this.category = json.getString(Keys.CATEGORY);
		this.implementation = json.getString(Keys.IMPLEMENTATION);
		this.details = copy(json.getJsonObject(Keys.DETAILS, new JsonObject()));
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.NAME, name)
			.put(Keys.CATEGORY, category)
			.put(Keys.IMPLEMENTATION, implementation)
			.put(Keys.DETAILS, copy(details));
	}

	private static JsonObject copy(JsonObject value) {
		return value == null ? new JsonObject() : value.copy();
	}

	public static final class Builder {
		private String name;
		private String category;
		private String implementation;
		private JsonObject details = new JsonObject();

		private Builder() {
		}

		public Builder withName(String value) {
			name = value;
			return this;
		}

		public Builder withCategory(String value) {
			category = value;
			return this;
		}

		public Builder withImplementation(String value) {
			implementation = value;
			return this;
		}

		public Builder withDetails(JsonObject value) {
			details = copy(value);
			return this;
		}

		public AdminInfrastructureItemView build() {
			AdminInfrastructureItemView view = new AdminInfrastructureItemView();
			view.name = Objects.requireNonNull(name, "name");
			view.category = Objects.requireNonNull(category, "category");
			view.implementation = Objects.requireNonNull(implementation, "implementation");
			view.details = copy(details);
			return view;
		}
	}
}

package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetListResult {
	public static final class Keys {
		public static final String TARGETS = "targets";

		private Keys() {
		}
	}

	private List<AdminTargetView> targets = List.of();

	public AdminTargetListResult() {
	}

	public AdminTargetListResult(JsonObject json) {
		this.targets = json.getJsonArray(Keys.TARGETS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminTargetView::new)
			.toList();
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.TARGETS, new JsonArray(targets.stream()
			.map(AdminTargetView::toJson)
			.toList()));
	}

	public List<AdminTargetView> getTargets() {
		return targets;
	}

	public AdminTargetListResult setTargets(List<AdminTargetView> targets) {
		this.targets = targets == null ? List.of() : List.copyOf(targets);
		return this;
	}
}

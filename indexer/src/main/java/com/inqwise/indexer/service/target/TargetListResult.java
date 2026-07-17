package com.inqwise.indexer.service.target;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetListResult {
	private List<TargetView> targets = List.of();

	public TargetListResult() {
	}

	public TargetListResult(JsonObject json) {
		targets = json.getJsonArray("targets", new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(TargetView::new)
			.toList();
	}

	public JsonObject toJson() {
		return new JsonObject().put("targets", new JsonArray(targets.stream()
			.map(TargetView::toJson)
			.toList()));
	}

	public List<TargetView> getTargets() {
		return targets;
	}

	public TargetListResult setTargets(List<TargetView> values) {
		targets = values == null ? List.of() : List.copyOf(values);
		return this;
	}
}

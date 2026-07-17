package com.inqwise.indexer.service.target;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetResult {
	private TargetView target;

	public TargetResult() {
	}

	public TargetResult(JsonObject json) {
		JsonObject targetJson = json.getJsonObject("target");
		target = targetJson == null ? null : new TargetView(targetJson);
	}

	public JsonObject toJson() {
		return new JsonObject().put("target", target == null ? null : target.toJson());
	}

	public TargetView getTarget() {
		return target;
	}

	public TargetResult setTarget(TargetView value) {
		target = value;
		return this;
	}
}

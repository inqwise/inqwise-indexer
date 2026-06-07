package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetResult {
	public static final class Keys {
		public static final String TARGET = "target";

		private Keys() {
		}
	}

	private AdminTargetView target;

	public AdminTargetResult() {
	}

	public AdminTargetResult(JsonObject json) {
		JsonObject targetJson = json.getJsonObject(Keys.TARGET);
		this.target = targetJson == null ? null : new AdminTargetView(targetJson);
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.TARGET, target == null ? null : target.toJson());
	}

	public AdminTargetView getTarget() {
		return target;
	}

	public AdminTargetResult setTarget(AdminTargetView target) {
		this.target = target;
		return this;
	}
}
